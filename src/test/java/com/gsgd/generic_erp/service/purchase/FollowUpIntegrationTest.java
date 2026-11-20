package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.dto.purchase.FollowUpRecordRequest;
import com.gsgd.generic_erp.dto.purchase.FollowUpTodoItem;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.enums.MaterialStatus;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRecordRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.PlannedCheckpoint;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;

/**
 * Runs the follow-up services against a real (H2) database, so the JPQL
 * queries are checked too. The clock can be moved between steps.
 */
@SpringJUnitConfig(FollowUpIntegrationTest.Config.class)
class FollowUpIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long ME = 7L;
    static final long OTHER = 8L;
    static final long MANAGER = 99L;
    /** Orders are placed on 2026-11-01, so day 1 is 11-02 and the default plan ends on 11-29. */
    static final LocalDate ORDER_DATE = LocalDate.of(2026, 11, 1);

    /** A clock the test can move; reads happen in the business zone. */
    static class MovableClock extends Clock {
        private Instant now = Instant.EPOCH;

        void set(LocalDateTime local) {
            now = local.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Collects notification events, the way NotificationDispatcher would receive them. */
    static class EventSink {
        final List<NotificationRequestedEvent> events = new ArrayList<>();

        @EventListener
        void on(NotificationRequestedEvent e) {
            events.add(e);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { PurchaseOrderRepository.class, UserRepository.class })
    @Import({ FollowUpRecordService.class, FollowUpReminderService.class, FollowUpRuleService.class,
            PurchaseOrderQueryService.class, PermissionChecker.class })
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPackagesToScan("com.gsgd.generic_erp.entity.purchase", "com.gsgd.generic_erp.entity.auth");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return f;
        }

        @Bean
        PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f) {
            return new JpaTransactionManager(f);
        }

        @Bean
        MovableClock clock() {
            return new MovableClock();
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(any())).thenReturn(Set.of(MANAGER));
            return r;
        }
    }

    @Autowired
    FollowUpRecordService records;
    @Autowired
    FollowUpReminderService reminders;
    @Autowired
    PurchaseOrderRepository orders;
    @Autowired
    FollowUpCheckpointRepository checkpoints;
    @Autowired
    FollowUpRecordRepository recordRepo;
    @Autowired
    com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository rules;
    @Autowired
    MovableClock clock;
    @Autowired
    EventSink sink;

    @BeforeEach
    void setUp() {
        recordRepo.deleteAll();
        checkpoints.deleteAll();
        orders.deleteAll();
        rules.deleteAll();
        sink.events.clear();
        loginAs(ME);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRecordInsideAnOpenWindowCompletesItAndASecondOneIsExtra() {
        PurchaseOrder po = orderWithPlan("PO-1", ME, PurchaseOrderStatus.PLACED);
        clock.set(LocalDateTime.of(2026, 11, 3, 10, 0)); // day 2, inside window 1 (days 1-3)

        var first = records.record(ME, po.getId(), req(MaterialStatus.ON_TRACK)).orElseThrow();
        assertNotNull(first.completedCheckpoint());
        assertEquals(1, first.completedCheckpoint().getSeq());
        assertEquals(CheckpointStatus.DONE, first.completedCheckpoint().getStatus());
        assertEquals(first.record().id(), first.completedCheckpoint().getDoneRecordId());

        var second = records.record(ME, po.getId(), req(MaterialStatus.AT_RISK)).orElseThrow();
        assertNull(second.completedCheckpoint());
        assertNull(second.record().checkpointId());

        var detail = records.followUps(ME, po.getId()).orElseThrow();
        assertEquals(2, detail.records().size());
        assertEquals(15, detail.checkpoints().size());
    }

    @Test
    void recordsAreRefusedForBadBodiesFinishedOrdersAndOtherPurchasersOrders() {
        PurchaseOrder mine = orderWithPlan("PO-1", ME, PurchaseOrderStatus.PLACED);
        PurchaseOrder received = orderWithPlan("PO-2", ME, PurchaseOrderStatus.RECEIVED);
        PurchaseOrder theirs = orderWithPlan("PO-3", OTHER, PurchaseOrderStatus.PLACED);
        clock.set(LocalDateTime.of(2026, 11, 3, 10, 0));

        assertEquals(400, assertThrows(InvalidFollowUpException.class,
                () -> records.record(ME, mine.getId(), req(null))).getStatus());
        assertEquals(400, assertThrows(InvalidFollowUpException.class,
                () -> records.record(ME, mine.getId(),
                        new FollowUpRecordRequest(MaterialStatus.ON_TRACK, null, false, "x".repeat(501))))
                .getStatus());
        assertEquals(409, assertThrows(InvalidFollowUpException.class,
                () -> records.record(ME, received.getId(), req(MaterialStatus.ON_TRACK))).getStatus());
        assertTrue(records.record(ME, theirs.getId(), req(MaterialStatus.ON_TRACK)).isEmpty());
        assertTrue(records.followUps(ME, theirs.getId()).isEmpty());
    }

    @Test
    void dueWindowsAreRemindedOnceAndEndedOnesBecomeMissed() {
        PurchaseOrder po = orderWithPlan("PO-1", ME, PurchaseOrderStatus.PLACED);

        clock.set(LocalDateTime.of(2026, 11, 2, 9, 0)); // day 1: window 1 opens
        var r1 = reminders.runOnce();
        assertEquals(new FollowUpReminderService.RunResult(0, 0, 1), r1);
        assertEquals(1, sink.events.size());
        NotificationRequestedEvent due = sink.events.get(0);
        assertEquals(NotificationType.FOLLOWUP_DUE, due.type());
        assertEquals(Set.of(ME), due.recipientIds());
        assertEquals(po.getId(), due.bizId());
        // Stored as keys and shown in each reader's language.
        assertEquals("1 follow-up is due", due.titleText().render(Locale.US));
        assertEquals("Please follow up on: PO-1 (by 2026-11-04)", due.contentText().render(Locale.US));
        assertEquals("请跟进：PO-1（2026-11-04 前）", due.contentText().render(Locale.SIMPLIFIED_CHINESE));

        clock.set(LocalDateTime.of(2026, 11, 2, 10, 0)); // an hour later: nothing new
        assertEquals(new FollowUpReminderService.RunResult(0, 0, 0), reminders.runOnce());
        assertEquals(1, sink.events.size());

        clock.set(LocalDateTime.of(2026, 11, 5, 9, 0)); // day 4: window 1 ended with no record
        sink.events.clear();
        var r2 = reminders.runOnce();
        assertEquals(1, r2.missed());
        assertEquals(1, r2.reminded()); // window 2 (days 4-7) opened
        assertEquals(CheckpointStatus.MISSED,
                checkpoints.findByPurchaseOrderIdOrderBySeqAsc(po.getId()).get(0).getStatus());
        List<NotificationRequestedEvent> missed = sink.events.stream()
                .filter(e -> e.type() == NotificationType.FOLLOWUP_MISSED).toList();
        assertEquals(Set.of(Set.of(ME), Set.of(MANAGER)),
                Set.copyOf(missed.stream().map(NotificationRequestedEvent::recipientIds).toList()));
    }

    @Test
    void anOrderPastItsPlanGetsOneOverdueWindowPerDay() {
        PurchaseOrder po = orders.save(order("PO-OLD", ME, PurchaseOrderStatus.PARTIALLY_RECEIVED));

        clock.set(LocalDateTime.of(2026, 12, 10, 9, 0)); // day 39, after the 28-day plan
        var r1 = reminders.runOnce();
        assertEquals(1, r1.overdueCreated());
        assertEquals(1, r1.reminded());
        assertEquals(0, reminders.runOnce().overdueCreated()); // same day: no second window

        clock.set(LocalDateTime.of(2026, 12, 11, 9, 0));
        var r2 = reminders.runOnce();
        assertEquals(1, r2.missed());
        assertEquals(1, r2.overdueCreated());

        List<FollowUpCheckpoint> list = checkpoints.findByPurchaseOrderIdOrderBySeqAsc(po.getId());
        assertEquals(2, list.size());
        assertEquals(FollowUpStage.OVERDUE, list.get(1).getStage());
        assertEquals(2, list.get(1).getSeq());
        assertEquals(LocalDate.of(2026, 12, 11), list.get(1).getWindowStart());
        assertEquals(LocalDate.of(2026, 12, 11), list.get(1).getWindowEnd());
        assertEquals(CheckpointStatus.MISSED, list.get(0).getStatus());
    }

    @Test
    void finishedOrdersAndOrdersStillInsideTheirPlanGetNoOverdueWindow() {
        orders.save(order("PO-DONE", ME, PurchaseOrderStatus.RECEIVED));
        PurchaseOrder young = orders.save(order("PO-NEW", ME, PurchaseOrderStatus.PLACED));
        young.setOrderDate(LocalDate.of(2026, 12, 1));
        orders.save(young);

        clock.set(LocalDateTime.of(2026, 12, 10, 9, 0));
        assertEquals(0, reminders.runOnce().overdueCreated());
    }

    @Test
    void aRuleWithNoOverduePeriodStopsAfterItsLastStage() {
        var rule = rules.save(com.gsgd.generic_erp.entity.purchase.FollowUpRule.builder()
                .ruleName("No overdue").defaultRule(false).active(true).overduePeriodDays(null)
                .createdAt(LocalDateTime.of(2026, 11, 1, 8, 0)).build());
        PurchaseOrder po = order("PO-NOOVD", ME, PurchaseOrderStatus.PLACED);
        po.setFollowupRuleId(rule.getId());
        orders.save(po);

        clock.set(LocalDateTime.of(2026, 12, 10, 9, 0));
        assertEquals(0, reminders.runOnce().overdueCreated());
        assertEquals(1, reminders.overduePeriodDays(null));
        assertEquals(1, reminders.overduePeriodDays(-5L));
    }

    @Test
    void theTodoListShowsOnlyMyOpenStartedWindowsEndingFirstOnTop() {
        PurchaseOrder a = orderWithPlan("PO-A", ME, PurchaseOrderStatus.PLACED);
        PurchaseOrder b = orderWithPlan("PO-B", ME, PurchaseOrderStatus.PLACED);
        b.setOrderDate(ORDER_DATE.plusDays(1));
        orders.save(b);
        checkpoints.findByPurchaseOrderIdOrderBySeqAsc(b.getId()).forEach(c -> {
            c.setWindowStart(c.getWindowStart().plusDays(1));
            c.setWindowEnd(c.getWindowEnd().plusDays(1));
            checkpoints.save(c);
        });
        orderWithPlan("PO-THEIRS", OTHER, PurchaseOrderStatus.PLACED);

        clock.set(LocalDateTime.of(2026, 11, 4, 9, 0)); // A: window 1 ends today; B: window 1 ends tomorrow
        List<FollowUpTodoItem> todo = records.todo(ME);
        assertEquals(List.of("PO-A", "PO-B"), todo.stream().map(FollowUpTodoItem::poNo).toList());
        assertEquals(0, todo.get(0).daysLeft());
        assertEquals(1, todo.get(1).daysLeft());
        assertEquals(a.getId(), todo.get(0).purchaseOrderId());
    }

    // ----- helpers -----

    private static FollowUpRecordRequest req(MaterialStatus status) {
        return new FollowUpRecordRequest(status, null, false, "called supplier");
    }

    private static PurchaseOrder order(String no, long purchaserId, PurchaseOrderStatus status) {
        return PurchaseOrder.builder()
                .sourceSystem("LEGACY_ERP")
                .externalPoNo(no)
                .supplierName("Supplier " + no)
                .purchaserId(purchaserId)
                .status(status)
                .orderDate(ORDER_DATE)
                .createdAt(LocalDateTime.of(2026, 11, 1, 8, 0))
                .build();
    }

    private PurchaseOrder orderWithPlan(String no, long purchaserId, PurchaseOrderStatus status) {
        PurchaseOrder po = orders.save(order(no, purchaserId, status));
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        checkpoints.saveAll(plan.stream().map(p -> FollowUpCheckpoint.builder()
                .purchaseOrderId(po.getId())
                .stage(p.stage())
                .seq(p.seq())
                .windowStart(p.windowStart())
                .windowEnd(p.windowEnd())
                .status(CheckpointStatus.OPEN)
                .createdAt(LocalDateTime.of(2026, 11, 1, 8, 0))
                .build()).toList());
        return po;
    }

    private static void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user" + userId, null,
                List.of(new SimpleGrantedAuthority("menu.purchaseOrder.read"),
                        new SimpleGrantedAuthority("menu.purchaseOrder.update"))));
    }
}

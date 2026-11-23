package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.gsgd.generic_erp.dto.purchase.PlacementTaskView;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.customer.CustomerOrder;
import com.gsgd.generic_erp.entity.customer.PoCustomerLink;
import com.gsgd.generic_erp.entity.purchase.PlacementTask;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;
import com.gsgd.generic_erp.pmc.AuditJson;
import com.gsgd.generic_erp.pmc.AuditTaskEntity;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.customer.PoCustomerLinkRepository;
import com.gsgd.generic_erp.repository.purchase.PlacementTaskRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService.PlacementException;

/**
 * Placement tasks against a real (H2) database: created from VERIFIED audits,
 * assigned, closed by a linked PO or by hand, reminded and escalated.
 */
@SpringJUnitConfig(PlacementTaskIntegrationTest.Config.class)
class PlacementTaskIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long MANAGER = 50L;
    static final long WANG = 7L; // active purchaser
    static final long LI = 8L; // purchaser account switched off
    static final long OTHER = 9L;

    /** A clock the tests can move forward. */
    static class MovableClock extends Clock {
        Instant now;

        MovableClock(LocalDateTime t) {
            set(t);
        }

        void set(LocalDateTime t) {
            now = t.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static class EventSink {
        final List<NotificationRequestedEvent> events = new ArrayList<>();

        @EventListener
        void on(NotificationRequestedEvent e) {
            events.add(e);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { CustomerOrderRepository.class, PlacementTaskRepository.class,
            AuditTaskRepository.class })
    @Import({ PlacementTaskService.class, AuditJson.class })
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPackagesToScan("com.gsgd.generic_erp.entity.customer", "com.gsgd.generic_erp.entity.purchase",
                    "com.gsgd.generic_erp.pmc");
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
            return new MovableClock(LocalDateTime.of(2026, 11, 20, 9, 0));
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(List.of(PlacementTaskService.MANAGER_ROLE))).thenReturn(Set.of(MANAGER));
            when(r.usersWithRoles(List.of(PlacementTaskService.PURCHASER_ROLE))).thenReturn(Set.of(WANG, LI));
            return r;
        }

        @Bean
        UserRepository userRepository() {
            User wang = User.builder().id(WANG).username("wang").displayName("王采购").status(true).build();
            User li = User.builder().id(LI).username("li").displayName("李采购").status(false).build();
            Map<Long, User> all = Map.of(WANG, wang, LI, li);
            UserRepository r = mock(UserRepository.class);
            when(r.findAllById(anyIterable())).thenAnswer(inv -> {
                List<User> out = new ArrayList<>();
                for (Object id : (Iterable<?>) inv.getArgument(0)) {
                    Optional.ofNullable(all.get(id)).ifPresent(out::add);
                }
                return out;
            });
            when(r.findById(any())).thenAnswer(inv -> Optional.ofNullable(all.get(inv.getArgument(0))));
            return r;
        }
    }

    @Autowired
    PlacementTaskService service;
    @Autowired
    PlacementTaskRepository tasks;
    @Autowired
    AuditTaskRepository audits;
    @Autowired
    PoCustomerLinkRepository links;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    MovableClock clock;
    @Autowired
    EventSink sink;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        audits.deleteAll();
        links.deleteAll();
        orders.deleteAll();
        sink.events.clear();
        clock.set(LocalDateTime.of(2026, 11, 20, 9, 0));
    }

    @Test
    void passingAuditMakesOneOpenTaskWithATwoDayDeadline() {
        // Verified at 08:30 Shanghai time.
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        audit("SO-2", 1, "VERIFIED", "FAIL", LocalDateTime.of(2026, 11, 20, 8, 0));
        audit("SO-3", 1, "SUBMITTED", null, null); // not verified yet: ignored

        var r = service.runOnce();
        assertEquals(1, r.created());
        assertEquals(1, r.skipped());

        PlacementTask t = tasks.findByStatus(PlacementTaskStatus.OPEN).get(0);
        assertEquals("SO-1", t.getOrderNo());
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 30), t.getDeadlineAt());

        assertEquals(1, sink.events.size());
        NotificationRequestedEvent e = sink.events.get(0);
        assertEquals(NotificationType.PO_PLACEMENT_DUE, e.type());
        assertEquals(Set.of(MANAGER), e.recipientIds());
        assertEquals("Assign a purchaser to place the purchase orders for customer order SO-1 by 2026-11-22 08:30",
                e.contentText().render(Locale.US));
        assertEquals("请指派采购员在 2026-11-22 08:30 前为客户订单 SO-1 下采购单",
                e.contentText().render(Locale.SIMPLIFIED_CHINESE));

        // A second passing round of the same order does not make a second task.
        audit("SO-1", 2, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 50));
        var again = service.runOnce();
        assertEquals(0, again.created());
        assertEquals(1, again.skipped());
        assertEquals(0, service.runOnce().skipped(), "each audit round is looked at once");
    }

    @Test
    void managerAssignsAndThePurchaserClosesByHand() {
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        service.runOnce();
        Long id = tasks.findAll().get(0).getId();
        sink.events.clear();

        assertEquals(Map.of(WANG, "王采购"), service.purchasers(), "switched-off accounts are not offered");
        assertEquals(400, assertThrows(PlacementException.class, () -> service.assign(id, LI, MANAGER)).getStatus());

        PlacementTaskView v = service.assign(id, WANG, MANAGER);
        assertEquals("王采购", v.assigneeName());
        assertEquals(Set.of(WANG), sink.events.get(0).recipientIds());

        assertEquals(403, assertThrows(PlacementException.class,
                () -> service.completeManually(id, "PO-1", OTHER, false)).getStatus());
        assertEquals(400, assertThrows(PlacementException.class,
                () -> service.completeManually(id, "  ", WANG, false)).getStatus());

        PlacementTaskView done = service.completeManually(id, " PO-1 ", WANG, false);
        assertEquals(PlacementTaskStatus.DONE, done.status());
        assertEquals("MANUAL", done.doneSource());
        assertEquals("PO-1", done.donePoNo());
        assertEquals(409, assertThrows(PlacementException.class,
                () -> service.completeManually(id, "PO-2", WANG, true)).getStatus());

        var mine = service.list(WANG, false, false, PageRequest.of(0, 20, Sort.by("id")));
        assertEquals(1, mine.getTotalElements());
        assertEquals(0, service.list(OTHER, false, false, PageRequest.of(0, 20, Sort.by("id"))).getTotalElements());
    }

    @Test
    void firstLinkedPoClosesTheTaskAndLineCoverageIsShown() {
        orders.save(CustomerOrder.builder().legacyId("E1").orderNo("SO-1").customerName("Acme")
                .orderDate(LocalDate.of(2026, 11, 18)).lineCount(3).legacyAudited(false).legacyClosed(false)
                .firstSeenAt(LocalDateTime.now(clock)).lastSyncedAt(LocalDateTime.now(clock)).build());
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        service.runOnce();

        PlacementTaskView open = service.list(MANAGER, true, true, PageRequest.of(0, 20, Sort.by("id")))
                .getContent().get(0);
        assertEquals("Acme", open.customerName());
        assertEquals(0, open.linesWithPo());
        assertEquals(3, open.lineCount());
        assertEquals(47, open.hoursLeft());

        links.save(link("L1", "PO-B", LocalDate.of(2026, 11, 21)));
        links.save(link("L1", "PO-A", LocalDate.of(2026, 11, 20)));
        links.save(link("L2", "PO-B", LocalDate.of(2026, 11, 21)));
        clock.set(LocalDateTime.of(2026, 11, 21, 10, 0));
        assertEquals(1, service.runOnce().closed());

        PlacementTaskView done = service.list(MANAGER, true, false, PageRequest.of(0, 20, Sort.by("id")))
                .getContent().get(0);
        assertEquals(PlacementTaskStatus.DONE, done.status());
        assertEquals("LINKED_PO", done.doneSource());
        assertEquals("PO-A", done.donePoNo(), "the earliest PO closes the task");
        assertEquals(List.of("PO-A", "PO-B"), done.linkedPoNos());
        assertEquals(2, done.linesWithPo());
        assertTrue(service.list(MANAGER, true, true, PageRequest.of(0, 20, Sort.by("id"))).isEmpty());
    }

    @Test
    void oneReminderBeforeTheDeadlineAndOneOverdueAlert() {
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        service.runOnce();
        sink.events.clear();

        clock.set(LocalDateTime.of(2026, 11, 21, 20, 0)); // 12.5 h left: not yet
        assertEquals(0, service.runOnce().reminded());
        clock.set(LocalDateTime.of(2026, 11, 21, 21, 0)); // 11.5 h left
        assertEquals(1, service.runOnce().reminded());
        assertEquals(0, service.runOnce().reminded(), "only one reminder");
        assertEquals(Set.of(MANAGER), sink.events.get(0).recipientIds(), "unassigned: managers are reminded");
        assertEquals(NotifactionPriority.HIGH, sink.events.get(0).priority());

        clock.set(LocalDateTime.of(2026, 11, 22, 8, 45));
        assertEquals(1, service.runOnce().overdue());
        assertEquals(0, service.runOnce().overdue(), "only one overdue alert");
        NotificationRequestedEvent e = sink.events.get(1);
        assertEquals(NotifactionPriority.ERGENT, e.priority());
        assertEquals("The purchase orders for customer order SO-1 were due by 2026-11-22 08:30 and are still not "
                + "placed (no purchaser assigned)", e.contentText().render(Locale.US));
        assertNull(tasks.findAll().get(0).getDoneAt());
    }

    private void audit(String orderNo, int round, String status, String result, LocalDateTime verifiedAt) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.id = UUID.randomUUID().toString();
        t.orderNumber = orderNo;
        t.round = round;
        t.createdBy = "1";
        t.status = status;
        t.effectiveDueAt = Instant.now();
        t.document = result == null ? "{}"
                : "{\"verificationResult\":\"" + result + "\",\"submissions\":[{\"verificationRecords\":[{\"result\":\""
                        + result + "\",\"verifiedBy\":\"1\",\"verifiedAt\":\""
                        + verifiedAt.atZone(ZONE).toInstant() + "\"}]}]}";
        audits.save(t);
    }

    private PoCustomerLink link(String line, String po, LocalDate date) {
        return PoCustomerLink.builder().customerOrderLegacyId("E1").customerOrderNo("SO-1")
                .customerLineLegacyId(line).poNo(po).poDate(date).firstSeenAt(LocalDateTime.now(clock)).build();
    }
}

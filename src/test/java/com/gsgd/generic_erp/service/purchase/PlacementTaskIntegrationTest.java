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
import com.gsgd.generic_erp.enums.PlacementOrderType;
import com.gsgd.generic_erp.enums.PlacementSource;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;
import com.gsgd.generic_erp.pmc.AuditJson;
import com.gsgd.generic_erp.pmc.AuditTaskEntity;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.customer.PoCustomerLinkRepository;
import com.gsgd.generic_erp.repository.purchase.PlacementTaskRepository;
import com.gsgd.generic_erp.service.auth.DepartmentPeers;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService.PlacementException;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPlacementSourceReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPlacementSourceRow;

/**
 * Placement tasks against a real (H2) database: created from VERIFIED audits
 * (no old-ERP queries) or from unconfirmed purchase applications / plans,
 * assigned, closed by a linked PO, a confirmation or by hand, reminded and
 * escalated.
 */
@SpringJUnitConfig(PlacementTaskIntegrationTest.Config.class)
class PlacementTaskIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long COLLEAGUE = 50L; // purchasing department member, no user row
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

    /** Stands in for the old ERP; a section is "configured" once rows are set for it. */
    static class FakeSourceReader implements LegacyPlacementSourceReader {
        final Map<PlacementOrderType, List<LegacyPlacementSourceRow>> rows = new java.util.EnumMap<>(
                PlacementOrderType.class);

        @Override
        public List<LegacyPlacementSourceRow> fetch(PlacementOrderType type, LocalDate since, int limit) {
            return rows.getOrDefault(type, List.of());
        }

        @Override
        public boolean supports(PlacementOrderType type) {
            return rows.containsKey(type);
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
        FakeSourceReader sourceReader() {
            return new FakeSourceReader();
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(List.of(PlacementTaskService.PURCHASER_ROLE))).thenReturn(Set.of(COLLEAGUE));
            return r;
        }

        /** COLLEAGUE, WANG and LI share the purchasing department; OTHER is elsewhere. */
        @Bean
        DepartmentPeers departmentPeers() {
            DepartmentPeers p = mock(DepartmentPeers.class);
            Set<Long> purchasing = Set.of(COLLEAGUE, WANG, LI);
            when(p.of(any())).thenAnswer(inv -> purchasing.contains(inv.getArgument(0)) ? purchasing
                    : Set.of((Long) inv.getArgument(0)));
            return p;
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
    @Autowired
    FakeSourceReader legacy;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        audits.deleteAll();
        links.deleteAll();
        orders.deleteAll();
        sink.events.clear();
        legacy.rows.clear();
        clock.set(LocalDateTime.of(2026, 11, 20, 9, 0));
    }

    // ----- trigger CONFIRMATION: old-ERP purchase applications / plans ----------

    @Test
    void applicationsAndPlansWithoutAPoMakeTasksInTheirOwnSection() {
        LocalDateTime d = LocalDateTime.of(2026, 11, 19, 10, 0);
        legacy.rows.put(PlacementOrderType.SAMPLE, List.of(
                row("R261113001", d, false, "26256样品单 FL36-400W 驱动", null),
                row("R261105004", d, true, "26252样品单", "YP261106001"))); // has a PO: no task
        legacy.rows.put(PlacementOrderType.PRODUCTION, List.of(
                row("26394-W53", d, false, null, null),
                row("26382-A", d, false, null, null)));
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30)); // audits no longer count

        var r = service.runOnce();
        assertEquals(3, r.created());
        assertEquals(3, tasks.count());

        PlacementTask sample = tasks.findAll().stream().filter(t -> t.getOrderNo().equals("R261113001"))
                .findFirst().orElseThrow();
        assertEquals(PlacementOrderType.SAMPLE, sample.getOrderType());
        assertEquals(PlacementSource.PURCHASE_APPLICATION, sample.getSource());
        assertEquals("R261113001", sample.getSourceNo());
        assertEquals("26256样品单 FL36-400W 驱动", sample.getSourceNote());
        assertEquals(d, sample.getStartedAt());
        assertEquals(d.plusDays(2), sample.getDeadlineAt(), "the 2 days start at the document date");
        assertNull(sample.getAuditTaskId());

        var samples = service.list(COLLEAGUE, true, true, PlacementOrderType.SAMPLE, PageRequest.of(0, 20,
                Sort.by("id")));
        assertEquals(List.of("R261113001"), samples.getContent().stream().map(PlacementTaskView::orderNo).toList());
        assertEquals("26256样品单 FL36-400W 驱动", samples.getContent().get(0).sourceNote());
        var production = service.list(COLLEAGUE, true, true, PlacementOrderType.PRODUCTION, PageRequest.of(0, 20,
                Sort.by("id")));
        assertEquals(List.of("26394-W53", "26382-A"), production.getContent().stream()
                .map(PlacementTaskView::orderNo).toList());
        assertEquals(Map.of(PlacementOrderType.PRODUCTION, 2L, PlacementOrderType.SAMPLE, 1L),
                service.openCounts(COLLEAGUE, true));

        NotificationRequestedEvent e = sink.events.stream()
                .filter(x -> x.titleText().render(Locale.US).contains("R261113001")).findFirst().orElseThrow();
        assertEquals("Sample order R261113001: purchase placement needs an owner", e.titleText().render(Locale.US));
        assertEquals("样品单 R261113001（26256样品单 FL36-400W 驱动）尚未下采购单。请认领或指派给本部门同事，并在 2026-11-21 10:00 前下采购单",
                e.contentText().render(Locale.SIMPLIFIED_CHINESE));

        assertEquals(0, service.runOnce().created(), "each document is turned into a task once");
    }

    @Test
    void theFirstPoClosesTheTaskAndAClosedTaskIsNotMadeAgain() {
        LocalDateTime d = LocalDateTime.of(2026, 11, 20, 8, 0);
        legacy.rows.put(PlacementOrderType.PRODUCTION, List.of(row("26391-W53", d, false, null, null),
                row("26392-W48", d, false, null, null)));
        service.runOnce();
        Long byHand = tasks.findAll().stream().filter(t -> t.getOrderNo().equals("26392-W48")).findFirst()
                .orElseThrow().getId();
        service.completeManually(byHand, "P261120001", WANG, false);

        legacy.rows.put(PlacementOrderType.PRODUCTION, List.of(row("26391-W53", d, true, null, "P261121004"),
                row("26392-W48", d, false, null, null)));
        var r = service.runOnce();
        assertEquals(1, r.confirmed());
        assertEquals(0, r.created(), "the task closed by hand is not made again");
        PlacementTask done = tasks.findAll().stream().filter(t -> t.getOrderNo().equals("26391-W53")).findFirst()
                .orElseThrow();
        assertEquals(PlacementTaskStatus.DONE, done.getStatus());
        assertEquals(PlacementTask.SOURCE_CONFIRMED, done.getDoneSource());
        assertEquals("P261121004", done.getDonePoNo());
        assertEquals(0, service.runOnce().confirmed());
    }

    @Test
    void anOrderWithAnOpenAuditTaskGetsNoSecondTask() {
        audit("26393-W52", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        assertEquals(1, service.runOnce().created(), "no queries yet: order audits still make tasks");
        assertEquals(PlacementOrderType.PRODUCTION, tasks.findAll().get(0).getOrderType());

        legacy.rows.put(PlacementOrderType.PRODUCTION, List.of(row("26393-W52", null, false, null, null)));
        var r = service.runOnce();
        assertEquals(0, r.created());
        assertEquals(1, r.skipped());
        assertEquals(1, tasks.count());
    }

    private static LegacyPlacementSourceRow row(String docNo, LocalDateTime date, boolean confirmed, String note,
            String firstPoNo) {
        return new LegacyPlacementSourceRow(docNo, docNo, date, confirmed, note, firstPoNo);
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
        assertEquals(Set.of(COLLEAGUE), e.recipientIds());
        assertEquals("Take this task or give it to a colleague: place the purchase orders for customer order SO-1 "
                + "by 2026-11-22 08:30", e.contentText().render(Locale.US));
        assertEquals("请认领或指派给本部门同事：在 2026-11-22 08:30 前为客户订单 SO-1 下采购单",
                e.contentText().render(Locale.SIMPLIFIED_CHINESE));

        // A second passing round of the same order does not make a second task.
        audit("SO-1", 2, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 50));
        var again = service.runOnce();
        assertEquals(0, again.created());
        assertEquals(1, again.skipped());
        assertEquals(0, service.runOnce().skipped(), "each audit round is looked at once");
    }

    @Test
    void aColleagueAssignsInsideTheDepartmentAndThePurchaserClosesByHand() {
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        service.runOnce();
        Long id = tasks.findAll().get(0).getId();
        sink.events.clear();

        assertEquals(Map.of(WANG, "王采购"), service.assignable(COLLEAGUE), "switched-off accounts are not offered");
        assertEquals(400, assertThrows(PlacementException.class, () -> service.assign(id, LI, COLLEAGUE)).getStatus());
        assertEquals(400, assertThrows(PlacementException.class, () -> service.assign(id, OTHER, COLLEAGUE)).getStatus(),
                "only yourself or a member of your department");

        PlacementTaskView v = service.assign(id, WANG, COLLEAGUE);
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

        var mine = service.list(WANG, false, false, null, PageRequest.of(0, 20, Sort.by("id")));
        assertEquals(1, mine.getTotalElements());
        assertEquals(1, service.list(COLLEAGUE, false, false, null, PageRequest.of(0, 20, Sort.by("id"))).getTotalElements(),
                "colleagues see the department's tasks");
        assertEquals(0, service.list(OTHER, false, false, null, PageRequest.of(0, 20, Sort.by("id"))).getTotalElements());
    }

    @Test
    void firstLinkedPoClosesTheTaskAndLineCoverageIsShown() {
        orders.save(CustomerOrder.builder().legacyId("E1").orderNo("SO-1").customerName("Acme")
                .orderDate(LocalDate.of(2026, 11, 18)).lineCount(3).legacyAudited(false).legacyClosed(false)
                .firstSeenAt(LocalDateTime.now(clock)).lastSyncedAt(LocalDateTime.now(clock)).build());
        audit("SO-1", 1, "VERIFIED", "PASS", LocalDateTime.of(2026, 11, 20, 8, 30));
        service.runOnce();

        PlacementTaskView open = service.list(COLLEAGUE, true, true, null, PageRequest.of(0, 20, Sort.by("id")))
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

        PlacementTaskView done = service.list(COLLEAGUE, true, false, null, PageRequest.of(0, 20, Sort.by("id")))
                .getContent().get(0);
        assertEquals(PlacementTaskStatus.DONE, done.status());
        assertEquals("LINKED_PO", done.doneSource());
        assertEquals("PO-A", done.donePoNo(), "the earliest PO closes the task");
        assertEquals(List.of("PO-A", "PO-B"), done.linkedPoNos());
        assertEquals(2, done.linesWithPo());
        assertTrue(service.list(COLLEAGUE, true, true, null, PageRequest.of(0, 20, Sort.by("id"))).isEmpty());
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
        assertEquals(Set.of(COLLEAGUE), sink.events.get(0).recipientIds(), "unassigned: the department is reminded");
        assertEquals(NotifactionPriority.HIGH, sink.events.get(0).priority());

        clock.set(LocalDateTime.of(2026, 11, 22, 8, 45));
        assertEquals(1, service.runOnce().overdue());
        assertEquals(0, service.runOnce().overdue(), "only one overdue alert");
        NotificationRequestedEvent e = sink.events.get(1);
        assertEquals(NotifactionPriority.ERGENT, e.priority());
        assertEquals("The purchase orders for order SO-1 were due by 2026-11-22 08:30 and are still not "
                + "placed (nobody has taken it yet)", e.contentText().render(Locale.US));
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

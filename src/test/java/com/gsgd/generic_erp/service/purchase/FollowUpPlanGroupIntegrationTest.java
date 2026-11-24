package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import com.gsgd.generic_erp.dto.purchase.PlanGroupOverview;
import com.gsgd.generic_erp.dto.purchase.PlanGroupRequest;
import com.gsgd.generic_erp.dto.purchase.PlanTypeRequest;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.enums.MaterialStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanGroupRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanTypeRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRecordRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleDayRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpTrackRepository;
import com.gsgd.generic_erp.repository.purchase.LegacySyncStateRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderLineRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.FollowUpIntegrationTest.EventSink;
import com.gsgd.generic_erp.service.purchase.FollowUpIntegrationTest.MovableClock;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;
import com.gsgd.generic_erp.service.purchase.FollowUpTrackService.RefreshResult;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderRow;

/**
 * Plan groups end to end on H2: the sync reads order lines, plan types are
 * grouped, and each separate group gets its own follow-up task series.
 */
@SpringJUnitConfig(FollowUpPlanGroupIntegrationTest.Config.class)
class FollowUpPlanGroupIntegrationTest {

    static final long ME = 7L;
    static final LocalDate ORDER_DATE = LocalDate.of(2026, 11, 1);

    /** Old ERP stand-in: the test sets the orders and lines it returns. */
    static class FakeLegacy implements LegacyPurchaseOrderReader, LegacyPurchaseOrderLineReader {
        final List<LegacyPurchaseOrderRow> orders = new ArrayList<>();
        final List<LegacyPurchaseOrderLineRow> lines = new ArrayList<>();

        @Override
        public List<LegacyPurchaseOrderRow> fetchChangedSince(LocalDateTime since, int limit) {
            return orders;
        }

        @Override
        public List<LegacyPurchaseOrderLineRow> fetchLines(Collection<String> poNos) {
            return lines.stream().filter(l -> poNos.contains(l.poNo())).toList();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { PurchaseOrderRepository.class, UserRepository.class })
    @Import({ FollowUpRecordService.class, FollowUpReminderService.class, FollowUpRuleService.class,
            FollowUpTrackService.class, FollowUpPlanGroupService.class, PurchaseOrderSyncService.class,
            PurchaseOrderQueryService.class, PermissionChecker.class, LegacyErpProperties.class })
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPackagesToScan("com.gsgd.generic_erp.entity.purchase", "com.gsgd.generic_erp.entity.auth",
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
            return new MovableClock();
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        FakeLegacy fakeLegacy() {
            return new FakeLegacy();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(any())).thenReturn(Set.of());
            return r;
        }
    }

    @Autowired
    PurchaseOrderSyncService sync;
    @Autowired
    FollowUpPlanGroupService groups;
    @Autowired
    FollowUpRecordService records;
    @Autowired
    FollowUpReminderService reminders;
    @Autowired
    FollowUpRuleService ruleService;
    @Autowired
    FakeLegacy legacy;
    @Autowired
    MovableClock clock;
    @Autowired
    EventSink sink;
    @Autowired
    PurchaseOrderRepository orders;
    @Autowired
    PurchaseOrderLineRepository lines;
    @Autowired
    FollowUpCheckpointRepository checkpoints;
    @Autowired
    FollowUpRecordRepository recordRepo;
    @Autowired
    FollowUpTrackRepository tracks;
    @Autowired
    FollowUpPlanGroupRepository groupRepo;
    @Autowired
    FollowUpPlanTypeRepository typeRepo;
    @Autowired
    FollowUpRuleRepository rules;
    @Autowired
    FollowUpRuleDayRepository ruleDays;
    @Autowired
    LegacySyncStateRepository syncState;
    @Autowired
    com.gsgd.generic_erp.repository.purchase.LegacyUserMapRepository userMap;

    @BeforeEach
    void setUp() {
        recordRepo.deleteAll();
        checkpoints.deleteAll();
        tracks.deleteAll();
        lines.deleteAll();
        orders.deleteAll();
        typeRepo.deleteAll();
        groupRepo.deleteAll();
        ruleDays.deleteAll();
        rules.deleteAll();
        syncState.deleteAll();
        userMap.deleteAll();
        legacy.orders.clear();
        legacy.lines.clear();
        sink.events.clear();
        userMap.save(com.gsgd.generic_erp.entity.purchase.LegacyUserMap.builder().sourceSystem("LEGACY_ERP")
                .legacyCode("buyer1").userId(ME).build());
        clock.set(LocalDateTime.of(2026, 11, 2, 9, 0)); // day 1
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user" + ME, null, List.of(new SimpleGrantedAuthority("menu.purchaseOrder.read"),
                        new SimpleGrantedAuthority("menu.purchaseOrder.update"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withoutGroupsEveryLineStaysInOneTaskAndTypesAreRegistered() {
        orderWithLines("PO-1", line(1, "HW", "五金"), line(2, "PK", "包材"));

        sync.syncNow();

        PurchaseOrder po = po("PO-1");
        assertEquals(2, lines.findByPurchaseOrderIdOrderByLineNoAsc(po.getId()).size());
        assertEquals(ids((Long) null), groupsOf(po));
        assertEquals(15, checkpoints.findByPurchaseOrderIdOrderBySeqAsc(po.getId()).size());
        PlanGroupOverview o = groups.overview();
        assertEquals(List.of("HW", "PK"), o.types().stream().map(PlanGroupOverview.Type::code).toList());
        assertEquals("五金", o.types().get(0).name());
        assertEquals(1, o.types().get(0).lineCount());
    }

    @Test
    void aSeparateGroupGetsItsOwnTasksAndTheRestStaysInTheMainTask() {
        Long hardware = separateGroup("Hardware", "HW", "SC");
        orderWithLines("PO-1", line(1, "HW", null), line(2, "SC", null), line(3, "PK", null));

        sync.syncNow();

        PurchaseOrder po = po("PO-1");
        assertEquals(ids(hardware, null), groupsOf(po));
        assertEquals(15, countOpen(po, null));
        assertEquals(15, countOpen(po, hardware));
        assertTrue(tracks.findByPurchaseOrderIdAndPlanGroupId(po.getId(), hardware).orElseThrow().isActive());

        // The to-do list shows both tasks of day 1, the group's with its name.
        List<FollowUpTodoItem> todo = records.todo(ME);
        assertEquals(2, todo.size());
        assertEquals(Set.of("Hardware"), todo.stream().map(FollowUpTodoItem::planGroupName)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        // A record for the group completes the group's task, not the main one.
        var saved = records.record(ME, po.getId(),
                new FollowUpRecordRequest(MaterialStatus.ON_TRACK, null, false, null, hardware)).orElseThrow();
        assertEquals(hardware, saved.completedCheckpoint().getPlanGroupId());
        assertEquals(hardware, saved.record().planGroupId());
        assertEquals(15, countOpen(po, null));

        var detail = records.followUps(ME, po.getId()).orElseThrow();
        assertEquals(2, detail.series().size());
        assertNull(detail.series().get(0).planGroupId());
        assertEquals(1, detail.series().get(0).lineCount());
        assertEquals("Hardware", detail.series().get(1).planGroupName());
        assertEquals(2, detail.series().get(1).lineCount());
    }

    @Test
    void aGroupThatIsNotSeparateStaysInTheMainTask() {
        Long id = separateGroup("Packaging", "PK");
        groups.updateGroup(id, new PlanGroupRequest("Packaging", false, null, null, null));
        orderWithLines("PO-1", line(1, "PK", null));

        sync.syncNow();

        assertEquals(ids((Long) null), groupsOf(po("PO-1")));
    }

    @Test
    void whenEveryLineIsInSeparateGroupsTheMainTaskIsNotPlanned() {
        Long hardware = separateGroup("Hardware", "HW");
        orderWithLines("PO-1", line(1, "HW", null));

        sync.syncNow();

        PurchaseOrder po = po("PO-1");
        assertFalse(po.isMainFollowupNeeded());
        assertEquals(Set.of(hardware), groupsOf(po));
        assertEquals(409, assertThrows(InvalidFollowUpException.class,
                () -> ruleService.applyToOrder(ME, po.getId(), ruleService.defaultRuleId(), null)).getStatus());
    }

    @Test
    void applyingNewGroupsSplitsOpenOrdersAndEndsSeriesThatAreNoLongerNeeded() {
        orderWithLines("PO-1", line(1, "HW", null), line(2, "PK", null));
        sync.syncNow();
        PurchaseOrder po = po("PO-1");
        clock.set(LocalDateTime.of(2026, 11, 5, 9, 0)); // day 4

        Long hardware = separateGroup("Hardware", "HW");
        RefreshResult r = groups.applyToOpenOrders();
        assertEquals(1, r.seriesStarted());
        // Only tasks that have not ended yet are planned for the new series.
        List<FollowUpCheckpoint> own = checkpoints.findByPurchaseOrderIdAndPlanGroupIdAndStatus(po.getId(), hardware,
                CheckpointStatus.OPEN);
        assertTrue(own.stream().noneMatch(c -> c.getWindowEnd().isBefore(LocalDate.of(2026, 11, 5))));
        assertEquals(14, own.size());

        // The group stops being separate: its future tasks go, the current one is cancelled.
        groups.updateGroup(hardware, new PlanGroupRequest("Hardware", false, null, null, null));
        RefreshResult r2 = groups.applyToOpenOrders();
        assertEquals(1, r2.seriesEnded());
        assertEquals(0, countOpen(po, hardware));
        assertEquals(1, checkpoints.findByPurchaseOrderIdAndPlanGroupIdAndStatus(po.getId(), hardware,
                CheckpointStatus.CANCELLED).size());
        assertFalse(tracks.findByPurchaseOrderIdAndPlanGroupId(po.getId(), hardware).orElseThrow().isActive());
    }

    @Test
    void aGroupWhoseLinesAreAllReceivedEndsItsSeriesOnTheNextSync() {
        Long hardware = separateGroup("Hardware", "HW");
        orderWithLines("PO-1", line(1, "HW", null), line(2, "PK", null));
        sync.syncNow();
        PurchaseOrder po = po("PO-1");
        assertEquals(15, countOpen(po, hardware));

        legacy.lines.set(0, new LegacyPurchaseOrderLineRow("PO-1", 1, "M1", "Bolt", "HW", null, 10.0, 10.0));
        sync.syncNow();

        assertEquals(0, countOpen(po, hardware));
        assertEquals(15, countOpen(po, null));
    }

    @Test
    void aGroupUsesItsOwnTemplateAndTheSeriesTemplateCanBeSwitched() {
        var t = ruleService.create(new com.gsgd.generic_erp.dto.purchase.FollowUpTemplateRequest("Fast", null, 10,
                List.of(2, 5), 1));
        Long hardware = groups.createGroup(new PlanGroupRequest("Hardware", true, t.id(), null, List.of("HW")))
                .groups().get(0).id();
        orderWithLines("PO-1", line(1, "HW", null), line(2, "PK", null));

        sync.syncNow();

        PurchaseOrder po = po("PO-1");
        List<FollowUpCheckpoint> own = checkpoints.findByPurchaseOrderIdAndPlanGroupIdAndStatus(po.getId(), hardware,
                CheckpointStatus.OPEN);
        assertEquals(2, own.size());
        assertTrue(own.stream().allMatch(c -> c.getStage() == FollowUpStage.NOTIFY));

        // Switch only the group's series back to the default template.
        int open = ruleService.applyToOrder(ME, po.getId(), ruleService.defaultRuleId(), hardware).orElseThrow();
        assertEquals(15, open);
        assertEquals(15, countOpen(po, null));
        assertEquals(404, assertThrows(InvalidFollowUpException.class,
                () -> ruleService.applyToOrder(ME, po.getId(), t.id(), hardware + 100)).getStatus());
    }

    @Test
    void overdueTasksAreAddedPerSeriesAndRemindersNameTheGroup() {
        Long hardware = separateGroup("Hardware", "HW");
        orderWithLines("PO-1", line(1, "HW", null), line(2, "PK", null));
        sync.syncNow();
        PurchaseOrder po = po("PO-1");

        clock.set(LocalDateTime.of(2026, 12, 1, 9, 0)); // day 30: past the default plan's day 28
        var run = reminders.runOnce();
        assertEquals(2, run.overdueCreated());
        assertEquals(1, countOpen(po, hardware));
        assertEquals(1, countOpen(po, null));

        NotificationRequestedEvent due = sink.events.stream()
                .filter(e -> e.type() == com.gsgd.generic_erp.enums.NotificationType.FOLLOWUP_DUE).findFirst()
                .orElseThrow();
        String en = due.contentText().render(Locale.US);
        assertTrue(en.contains("PO-1 [Hardware] (by 2026-12-01)"), en);
        assertTrue(due.contentText().render(Locale.SIMPLIFIED_CHINESE).contains("PO-1【Hardware】（2026-12-01 前）"));
    }

    @Test
    void typesCanBeAddedByHandAndGroupNamesMustBeUnique() {
        groups.addType(new PlanTypeRequest("EL", "电子料", null));
        assertEquals(409, assertThrows(InvalidFollowUpException.class,
                () -> groups.addType(new PlanTypeRequest("EL", null, null))).getStatus());
        Long id = separateGroup("Electronics", "EL");
        assertEquals(409, assertThrows(InvalidFollowUpException.class,
                () -> groups.createGroup(new PlanGroupRequest("electronics", true, null, null, null))).getStatus());
        assertEquals(400, assertThrows(InvalidFollowUpException.class,
                () -> groups.createGroup(new PlanGroupRequest(" ", true, null, null, null))).getStatus());

        PlanGroupOverview o = groups.removeGroup(id);
        assertTrue(o.groups().isEmpty());
        assertNull(o.types().get(0).groupId());
    }

    // ----- helpers ------------------------------------------------------------

    private Long separateGroup(String name, String... codes) {
        return groups.createGroup(new PlanGroupRequest(name, true, null, null, List.of(codes))).groups().stream()
                .filter(g -> g.name().equals(name)).findFirst().orElseThrow().id();
    }

    private void orderWithLines(String no, LegacyPurchaseOrderLineRow... ls) {
        legacy.orders.add(new LegacyPurchaseOrderRow(no, "S001", "Supplier A", "buyer1", ORDER_DATE,
                ORDER_DATE.plusDays(28), "PLACED", LocalDateTime.of(2026, 11, 1, 9, 0)));
        for (LegacyPurchaseOrderLineRow l : ls) {
            legacy.lines.add(new LegacyPurchaseOrderLineRow(no, l.lineNo(), l.materialCode(), l.materialName(),
                    l.planType(), l.planTypeName(), l.qty(), l.receivedQty()));
        }
    }

    private static LegacyPurchaseOrderLineRow line(int no, String type, String typeName) {
        return new LegacyPurchaseOrderLineRow(null, no, "M" + no, "Material " + no, type, typeName, 10.0, 0.0);
    }

    private PurchaseOrder po(String no) {
        return orders.findBySourceSystemAndExternalPoNo("LEGACY_ERP", no).orElseThrow();
    }

    private static Set<Long> ids(Long... ids) {
        return new java.util.HashSet<>(java.util.Arrays.asList(ids));
    }

    private Set<Long> groupsOf(PurchaseOrder po) {
        return checkpoints.findByPurchaseOrderIdOrderBySeqAsc(po.getId()).stream()
                .filter(c -> c.getStatus() == CheckpointStatus.OPEN)
                .map(FollowUpCheckpoint::getPlanGroupId).collect(java.util.HashSet::new, Set::add, Set::addAll);
    }

    private int countOpen(PurchaseOrder po, Long groupId) {
        return checkpoints.findByPurchaseOrderIdAndPlanGroupIdAndStatus(po.getId(), groupId, CheckpointStatus.OPEN)
                .size();
    }
}

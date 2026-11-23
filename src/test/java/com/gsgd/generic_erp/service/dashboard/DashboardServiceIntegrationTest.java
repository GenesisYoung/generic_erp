package com.gsgd.generic_erp.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.gsgd.generic_erp.dto.dashboard.PmcDashboard;
import com.gsgd.generic_erp.dto.dashboard.PurchasingDashboard;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.customer.CustomerOrder;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PlacementTask;
import com.gsgd.generic_erp.entity.purchase.PurchaseException;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;
import com.gsgd.generic_erp.enums.PurchaseExceptionCategory;
import com.gsgd.generic_erp.enums.PurchaseExceptionSeverity;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.pmc.AuditTaskEntity;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.PlacementTaskRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;

/** Dashboard numbers against a real (H2) database, so every JPQL count is checked. */
@SpringJUnitConfig(DashboardServiceIntegrationTest.Config.class)
class DashboardServiceIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    static final LocalDate TODAY = NOW.toLocalDate();
    static final long WANG = 7L;
    static final long ZHAO = 8L;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { PurchaseOrderRepository.class, CustomerOrderRepository.class,
            AuditTaskRepository.class })
    @Import(DashboardService.class)
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
        Clock clock() {
            return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        }

        @Bean
        UserRepository userRepository() {
            Map<Long, User> all = Map.of(
                    WANG, User.builder().id(WANG).username("wang").displayName("王采购").status(true).build(),
                    ZHAO, User.builder().id(ZHAO).username("zhao").displayName("").status(true).build());
            UserRepository r = mock(UserRepository.class);
            when(r.findAllById(anyIterable())).thenAnswer(inv -> {
                List<User> out = new ArrayList<>();
                for (Object id : (Iterable<?>) inv.getArgument(0)) {
                    Optional.ofNullable(all.get(id)).ifPresent(out::add);
                }
                return out;
            });
            return r;
        }
    }

    @Autowired
    DashboardService service;
    @Autowired
    CustomerOrderRepository customerOrders;
    @Autowired
    AuditTaskRepository audits;
    @Autowired
    PlacementTaskRepository placements;
    @Autowired
    PurchaseExceptionRepository exceptions;
    @Autowired
    PurchaseOrderRepository orders;
    @Autowired
    FollowUpCheckpointRepository checkpoints;

    @BeforeEach
    void setUp() {
        checkpoints.deleteAll();
        exceptions.deleteAll();
        orders.deleteAll();
        placements.deleteAll();
        audits.deleteAll();
        customerOrders.deleteAll();

        customerOrder("SO-1", false, NOW.minusHours(2)); // new today, no audit
        customerOrder("SO-2", false, NOW.minusDays(3)); // has an audit
        customerOrder("SO-3", true, NOW.minusDays(3)); // closed
        audit("SO-2", "IN_PROGRESS", NOW.minusHours(1)); // overdue
        audit("SO-4", "SUBMITTED", NOW.plusDays(1));
        audit("SO-5", "VERIFIED", NOW.minusDays(1));

        placement("SO-5", PlacementTaskStatus.OPEN, NOW.minusHours(3), null, null); // overdue, unassigned
        placement("SO-6", PlacementTaskStatus.OPEN, NOW.plusHours(5), WANG, null); // due soon
        placement("SO-7", PlacementTaskStatus.OPEN, NOW.plusDays(2), WANG, null);
        placement("SO-8", PlacementTaskStatus.DONE, NOW.minusDays(1), ZHAO, NOW.minusDays(2)); // on time
        placement("SO-9", PlacementTaskStatus.DONE, NOW.minusDays(5), ZHAO, NOW.minusDays(4)); // late

        Long poW = po("PO-W", WANG, PurchaseOrderStatus.PLACED);
        Long poZ = po("PO-Z", ZHAO, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po("PO-OLD", WANG, PurchaseOrderStatus.RECEIVED);
        checkpoint(poW, CheckpointStatus.OPEN, TODAY.minusDays(1), TODAY.plusDays(1)); // due today
        checkpoint(poW, CheckpointStatus.MISSED, TODAY.minusDays(6), TODAY.minusDays(3));
        checkpoint(poZ, CheckpointStatus.OPEN, TODAY, TODAY); // due today
        checkpoint(poZ, CheckpointStatus.MISSED, TODAY.minusDays(20), TODAY.minusDays(18)); // too old

        exception(poW, WANG, PurchaseExceptionSeverity.URGENT, PurchaseExceptionStatus.REPORTED);
        exception(poZ, ZHAO, PurchaseExceptionSeverity.HIGH, PurchaseExceptionStatus.IN_PROGRESS);
        exception(poZ, ZHAO, PurchaseExceptionSeverity.NORMAL, PurchaseExceptionStatus.CLOSED);
    }

    @Test
    void pmcDashboardCountsEverything() {
        PmcDashboard d = service.pmc();
        assertEquals(1, d.customerOrders().pendingAudit());
        assertEquals(1, d.customerOrders().newToday());
        assertNull(d.customerOrders().lastSyncAt());

        assertEquals(Map.of("IN_PROGRESS", 1L, "SUBMITTED", 1L), d.audits().byStatus());
        assertEquals(1, d.audits().overdue());

        assertEquals(3, d.placement().open());
        assertEquals(1, d.placement().overdue());
        assertEquals(1, d.placement().dueSoon());
        assertEquals(1, d.placement().unassigned());
        assertEquals(2, d.placement().doneLast30Days());
        assertEquals(0.5, d.placement().onTimeRate());

        assertEquals(2, d.exceptions().open());
        assertEquals(Map.of("NORMAL", 0L, "HIGH", 1L, "URGENT", 1L), d.exceptions().openBySeverity());
        assertEquals(1, d.exceptions().openByStatus().get("REPORTED"));
        assertEquals(List.of("SO-5"), d.overduePlacements().stream().map(PmcDashboard.PlacementRow::orderNo).toList());
        assertEquals(List.of("URGENT", "HIGH"),
                d.urgentExceptions().stream().map(PmcDashboard.ExceptionRow::severity).toList());
    }

    @Test
    void purchasingManagerSeesEveryPurchaser() {
        PurchasingDashboard d = service.purchasing(99L, true);
        assertTrue(d.allPurchasers());
        assertEquals(3, d.placement().open());
        assertEquals(1, d.placement().unassigned());
        assertEquals(2, d.followUps().activeOrders());
        assertEquals(2, d.followUps().dueToday());
        assertEquals(1, d.followUps().missedLast7Days());
        assertEquals(2, d.exceptions().open());
        assertEquals(1, d.exceptions().urgentOpen());

        assertEquals(List.of(WANG, ZHAO),
                d.byPurchaser().stream().map(PurchasingDashboard.PurchaserRow::purchaserId).toList());
        PurchasingDashboard.PurchaserRow wang = d.byPurchaser().get(0);
        assertEquals("王采购", wang.name());
        assertEquals(1, wang.activeOrders());
        assertEquals(1, wang.missedLast7Days());
        assertEquals(2, wang.openPlacements());
        assertEquals("zhao", d.byPurchaser().get(1).name(), "falls back to the username");
    }

    @Test
    void purchaserSeesOnlyTheirOwnWork() {
        PurchasingDashboard d = service.purchasing(WANG, false);
        assertEquals(2, d.placement().open());
        assertEquals(0, d.placement().overdue());
        assertEquals(1, d.placement().dueSoon());
        assertEquals(1, d.followUps().activeOrders());
        assertEquals(1, d.followUps().dueToday());
        assertEquals(1, d.exceptions().open());
        assertTrue(d.byPurchaser().isEmpty());
        assertEquals(List.of("PO-W"), d.dueFollowUps().stream().map(PurchasingDashboard.FollowUpRow::poNo).toList());
        assertEquals(1, d.dueFollowUps().get(0).daysLeft());
        assertEquals(List.of("SO-6", "SO-7"),
                d.openPlacements().stream().map(PmcDashboard.PlacementRow::orderNo).toList());
    }

    private void customerOrder(String no, boolean closed, LocalDateTime seen) {
        customerOrders.save(CustomerOrder.builder().legacyId("E-" + no).orderNo(no).orderDate(TODAY)
                .legacyAudited(false).legacyClosed(closed).firstSeenAt(seen).lastSyncedAt(seen).build());
    }

    private void audit(String orderNo, String status, LocalDateTime due) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.id = UUID.randomUUID().toString();
        t.orderNumber = orderNo;
        t.round = 1;
        t.createdBy = "1";
        t.status = status;
        t.document = "{}";
        t.effectiveDueAt = due.atZone(ZONE).toInstant();
        audits.save(t);
    }

    private void placement(String orderNo, PlacementTaskStatus status, LocalDateTime deadline, Long assignee,
            LocalDateTime doneAt) {
        placements.save(PlacementTask.builder().auditTaskId(UUID.randomUUID().toString()).auditRound(1)
                .orderNo(orderNo).auditResult("PASS").auditVerifiedAt(deadline.minusDays(2)).deadlineAt(deadline)
                .status(status).assigneeId(assignee).doneAt(doneAt).createdAt(NOW.minusDays(10)).build());
    }

    private Long po(String no, Long purchaser, PurchaseOrderStatus status) {
        return orders.save(PurchaseOrder.builder().sourceSystem("LEGACY_ERP").externalPoNo(no).supplierName("S")
                .purchaserId(purchaser).status(status).orderDate(TODAY.minusDays(10))
                .createdAt(NOW.minusDays(10)).build()).getId();
    }

    private void checkpoint(Long poId, CheckpointStatus status, LocalDate start, LocalDate end) {
        checkpoints.save(FollowUpCheckpoint.builder().purchaseOrderId(poId).stage(FollowUpStage.INITIAL).seq(1)
                .windowStart(start).windowEnd(end).status(status).createdAt(NOW.minusDays(10)).build());
    }

    private void exception(Long poId, Long reporter, PurchaseExceptionSeverity sev, PurchaseExceptionStatus status) {
        exceptions.save(PurchaseException.builder().purchaseOrderId(poId).poNo("PO").reporterId(reporter)
                .category(PurchaseExceptionCategory.DELAY).severity(sev).description("x").status(status)
                .reportedAt(NOW.minusDays(1)).updatedAt(NOW.minusDays(1)).build());
    }
}

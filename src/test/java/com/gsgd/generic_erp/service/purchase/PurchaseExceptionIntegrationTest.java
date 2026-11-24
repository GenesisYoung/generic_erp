package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionActionRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionReportRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionView;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PurchaseExceptionAction;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionApprovalRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionLogRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.PurchaseExceptionService.Actor;
import com.gsgd.generic_erp.service.purchase.PurchaseExceptionService.PurchaseExceptionError;

/**
 * Purchasing exceptions against a real (H2) database: reporting, the status
 * flow with its permission rules, data scope and who is alerted.
 */
@SpringJUnitConfig(PurchaseExceptionIntegrationTest.Config.class)
class PurchaseExceptionIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long WANG = 7L; // purchaser of PO-1
    static final long ZHAO = 8L; // another purchaser
    static final long PMC = 20L; // pmc_manager / pmc_clerk: handles
    static final long BOSS = 50L; // purchaser_manager
    static final long PLANNER = 60L; // Planning department user
    static final long APPROVER = 70L; // PMC department member: approves resolutions

    static final Actor WANG_A = new Actor(WANG, false, false, true, false, false);
    static final Actor ZHAO_A = new Actor(ZHAO, false, false, true, false, false);
    static final Actor PMC_A = new Actor(PMC, true, true, false, false, false);
    static final Actor BOSS_A = new Actor(BOSS, true, false, true, true, false);
    static final Actor APPROVER_A = new Actor(APPROVER, false, false, false, false, true);

    static class EventSink {
        final List<NotificationRequestedEvent> events = new ArrayList<>();

        @EventListener
        void on(NotificationRequestedEvent e) {
            events.add(e);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { PurchaseOrderRepository.class, CustomerOrderRepository.class,
            AuditTaskRepository.class })
    @Import(PurchaseExceptionService.class)
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
            return Clock.fixed(LocalDateTime.of(2026, 11, 20, 10, 0).atZone(ZONE).toInstant(), ZONE);
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(any())).thenReturn(Set.of(PMC, BOSS));
            when(r.usersInDepartments(any())).thenReturn(Set.of(PLANNER));
            return r;
        }

        @Bean
        ExceptionApprovers approvers() {
            ExceptionApprovers a = mock(ExceptionApprovers.class);
            when(a.users()).thenAnswer(inv -> new java.util.LinkedHashSet<>(Set.of(APPROVER, PMC)));
            return a;
        }

        @Bean
        UserRepository userRepository() {
            Map<Long, User> all = Map.of(
                    WANG, User.builder().id(WANG).username("wang").displayName("王采购").status(true).build(),
                    PMC, User.builder().id(PMC).username("pmc").displayName("PMC 李").status(true).build(),
                    APPROVER, User.builder().id(APPROVER).username("appr").displayName("PMC 审批").status(true)
                            .build());
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
    PurchaseExceptionService service;
    @Autowired
    PurchaseExceptionRepository exceptions;
    @Autowired
    PurchaseExceptionLogRepository logs;
    @Autowired
    PurchaseExceptionApprovalRepository approvals;
    @Autowired
    PurchaseOrderRepository orders;
    @Autowired
    EventSink sink;

    Long po1;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        approvals.deleteAll();
        exceptions.deleteAll();
        orders.deleteAll();
        sink.events.clear();
        po1 = orders.save(PurchaseOrder.builder().sourceSystem("LEGACY_ERP").externalPoNo("PO-1")
                .supplierCode("S9").supplierName("华东五金").purchaserId(WANG).status(PurchaseOrderStatus.PLACED)
                .orderDate(LocalDate.of(2026, 11, 1)).createdAt(LocalDateTime.of(2026, 11, 1, 9, 0)).build())
                .getId();
    }

    @Test
    void reportingAlertsEveryoneAtOnceInTheirLanguage() {
        PurchaseExceptionView v = service.report(WANG_A, new PurchaseExceptionReportRequest(null, " PO-1 ", "delay",
                "URGENT", "供应商说要晚一周", LocalDate.of(2026, 12, 5)));

        assertEquals("EX-261120-" + String.format("%04d", v.id()), v.exceptionNo());
        assertEquals(PurchaseExceptionStatus.REPORTED, v.status());
        assertEquals("华东五金", v.supplierName());
        assertEquals("王采购", v.reporterName());
        assertEquals(List.of(PurchaseExceptionAction.COMMENT), v.allowedActions(), "the reporter can only comment");
        assertEquals(1, v.history().size());

        NotificationRequestedEvent e = sink.events.get(0);
        assertEquals(NotificationType.EXCEPTION_REPORTED, e.type());
        assertEquals(NotifactionPriority.ERGENT, e.priority());
        assertEquals(Set.of(WANG, PMC, BOSS, PLANNER), e.recipientIds(), "reporter gets a confirmation");
        assertEquals("[Urgent] Purchasing exception on PO PO-1: Delivery delay", e.titleText().render(Locale.US));
        assertEquals("【紧急】采购单 PO-1 出现异常：交期延误", e.titleText().render(Locale.SIMPLIFIED_CHINESE));
        assertEquals(v.exceptionNo() + " · Supplier 华东五金: 供应商说要晚一周 (affects 2026-12-05)",
                e.contentText().render(Locale.US));
    }

    @Test
    void reportingIsCheckedAndScoped() {
        var ok = new PurchaseExceptionReportRequest(po1, null, "QUALITY", null, "划痕", null);
        assertEquals(404, assertThrows(PurchaseExceptionError.class, () -> service.report(ZHAO_A, ok)).getStatus(),
                "not his order");
        assertEquals(403, assertThrows(PurchaseExceptionError.class, () -> service.report(PMC_A, ok)).getStatus());
        assertEquals(400, assertThrows(PurchaseExceptionError.class, () -> service.report(WANG_A,
                new PurchaseExceptionReportRequest(po1, null, "WEATHER", null, "x", null))).getStatus());
        assertEquals(400, assertThrows(PurchaseExceptionError.class, () -> service.report(WANG_A,
                new PurchaseExceptionReportRequest(po1, null, "QUALITY", null, "  ", null))).getStatus());

        PurchaseExceptionView v = service.report(BOSS_A, ok);
        assertEquals("NORMAL", v.severity().name());

        var page = PageRequest.of(0, 20, Sort.by("id"));
        assertEquals(1, service.list(WANG_A, true, page).getTotalElements(), "on his order");
        assertEquals(0, service.list(ZHAO_A, true, page).getTotalElements());
        assertEquals(1, service.list(PMC_A, true, page).getTotalElements());
        assertEquals(404, assertThrows(PurchaseExceptionError.class, () -> service.detail(ZHAO_A, v.id()))
                .getStatus());
    }

    @Test
    void handledResolvedThenClosedOrReopened() {
        Long id = service.report(WANG_A,
                new PurchaseExceptionReportRequest(po1, null, "SHORTAGE", "HIGH", "少 200 件", null)).id();
        sink.events.clear();

        assertEquals(409, assertThrows(PurchaseExceptionError.class,
                () -> service.act(WANG_A, id, new PurchaseExceptionActionRequest("ACKNOWLEDGE", null, null)))
                .getStatus(), "a purchaser cannot handle");

        PurchaseExceptionView ack = service.act(PMC_A, id, new PurchaseExceptionActionRequest("acknowledge", null, 1L));
        assertEquals(PurchaseExceptionStatus.ACKNOWLEDGED, ack.status());
        assertEquals("PMC 李", ack.handlerName());
        NotificationRequestedEvent e = sink.events.get(0);
        assertEquals(NotificationType.EXCEPTION_UPDATED, e.type());
        assertFalse(e.recipientIds().contains(PMC), "the one who acted is not alerted");
        assertTrue(e.recipientIds().containsAll(Set.of(WANG, BOSS, PLANNER)));
        assertEquals("PMC 李 acknowledged it", e.contentText().render(Locale.US));

        assertEquals(409, assertThrows(PurchaseExceptionError.class,
                () -> service.act(PMC_A, id, new PurchaseExceptionActionRequest("START", null, 1L))).getStatus(),
                "stale version");
        service.act(PMC_A, id, new PurchaseExceptionActionRequest("START", null, null));
        assertEquals(400, assertThrows(PurchaseExceptionError.class,
                () -> service.act(PMC_A, id, new PurchaseExceptionActionRequest("RESOLVE", " ", null))).getStatus());
        service.act(PMC_A, id, new PurchaseExceptionActionRequest("RESOLVE", "改由备选供应商补 200 件", null));
        PurchaseExceptionView resolved = service.act(APPROVER_A, id,
                new PurchaseExceptionActionRequest("APPROVE", null, null));
        assertEquals(PurchaseExceptionStatus.RESOLVED, resolved.status());
        assertEquals("改由备选供应商补 200 件", resolved.resolution());
        assertEquals(List.of(PurchaseExceptionAction.COMMENT),
                service.detail(PMC_A, id).allowedActions(),
                "PMC does not close; the reporter or the purchasing manager does");

        sink.events.clear();
        service.act(WANG_A, id, new PurchaseExceptionActionRequest("COMMENT", "已确认到货时间", null));
        assertEquals(Set.of(PMC), sink.events.get(0).recipientIds(), "a comment alerts the handler only");

        PurchaseExceptionView reopened = service.act(WANG_A, id,
                new PurchaseExceptionActionRequest("REOPEN", "备选供应商也缺货", null));
        assertEquals(PurchaseExceptionStatus.IN_PROGRESS, reopened.status());
        service.act(PMC_A, id, new PurchaseExceptionActionRequest("RESOLVE", "分两批交货", null));
        assertEquals(409, assertThrows(PurchaseExceptionError.class,
                () -> service.act(BOSS_A, id, new PurchaseExceptionActionRequest("CLOSE", null, null))).getStatus(),
                "not resolved until PMC approves");
        service.act(APPROVER_A, id, new PurchaseExceptionActionRequest("APPROVE", "同意", null));
        PurchaseExceptionView closed = service.act(BOSS_A, id, new PurchaseExceptionActionRequest("CLOSE", null, null));
        assertEquals(PurchaseExceptionStatus.CLOSED, closed.status());
        assertTrue(closed.allowedActions().isEmpty());
        assertEquals(List.of("REPORT", "ACKNOWLEDGE", "START", "RESOLVE", "APPROVE", "COMMENT", "REOPEN", "RESOLVE",
                "APPROVE", "CLOSE"), closed.history().stream().map(h -> h.action().name()).toList());
        assertEquals(2, closed.approvals().size(), "each resolution got its own approval task");
        assertEquals(0, service.openCount(PMC_A));
    }

    @Test
    void everyResolutionNeedsPmcApproval() {
        Long id = service.report(WANG_A,
                new PurchaseExceptionReportRequest(po1, null, "DELAY", "URGENT", "晚到 5 天", null)).id();
        sink.events.clear();

        PurchaseExceptionView pending = service.act(PMC_A, id,
                new PurchaseExceptionActionRequest("RESOLVE", "空运补货", null));
        assertEquals(PurchaseExceptionStatus.PENDING_APPROVAL, pending.status());
        assertEquals(null, pending.resolvedAt(), "not resolved yet");
        assertEquals(1, pending.approvals().size());
        assertEquals("PENDING", pending.approvals().get(0).status().name());
        assertEquals("PMC 李", pending.approvals().get(0).requestedByName());
        assertEquals(List.of(PurchaseExceptionAction.COMMENT), pending.allowedActions(),
                "the handler cannot approve their own proposal without being in PMC");

        NotificationRequestedEvent task = sink.events.stream()
                .filter(ev -> ev.type() == NotificationType.EXCEPTION_APPROVAL).findFirst().orElseThrow();
        assertEquals(Set.of(APPROVER), task.recipientIds(), "PMC department, not the one who proposed");
        assertEquals(NotifactionPriority.ERGENT, task.priority());
        assertEquals("PMC approval needed: exception " + pending.exceptionNo() + " (PO PO-1)",
                task.titleText().render(Locale.US));
        assertEquals("待 PMC 审批：异常 " + pending.exceptionNo() + "（采购单 PO-1）",
                task.titleText().render(Locale.SIMPLIFIED_CHINESE));
        NotificationRequestedEvent update = sink.events.stream()
                .filter(ev -> ev.type() == NotificationType.EXCEPTION_UPDATED).findFirst().orElseThrow();
        assertFalse(update.recipientIds().contains(APPROVER), "approvers get the task, not a second alert");

        var page = PageRequest.of(0, 20, Sort.by("id"));
        assertEquals(1, service.list(APPROVER_A, PurchaseExceptionService.ListView.APPROVAL, page)
                .getTotalElements(), "PMC sees the approval task");
        assertEquals(1, service.approvalCount(APPROVER_A));
        assertEquals(0, service.approvalCount(PMC_A));
        assertEquals(1, service.openCount(WANG_A), "still open while waiting");
        assertEquals(List.of(PurchaseExceptionAction.APPROVE, PurchaseExceptionAction.REJECT,
                PurchaseExceptionAction.COMMENT), service.detail(APPROVER_A, id).allowedActions());

        assertEquals(409, assertThrows(PurchaseExceptionError.class,
                () -> service.act(PMC_A, id, new PurchaseExceptionActionRequest("APPROVE", null, null))).getStatus(),
                "only the PMC department approves");
        assertEquals(400, assertThrows(PurchaseExceptionError.class,
                () -> service.act(APPROVER_A, id, new PurchaseExceptionActionRequest("REJECT", " ", null)))
                .getStatus(), "a rejection needs a reason");

        PurchaseExceptionView rejected = service.act(APPROVER_A, id,
                new PurchaseExceptionActionRequest("REJECT", "空运成本太高", null));
        assertEquals(PurchaseExceptionStatus.IN_PROGRESS, rejected.status());
        assertEquals(null, rejected.resolution());
        assertEquals("REJECTED", rejected.approvals().get(0).status().name());
        assertEquals("空运成本太高", rejected.approvals().get(0).decisionComment());
        assertEquals("PMC 审批", rejected.approvals().get(0).decidedByName());

        service.act(PMC_A, id, new PurchaseExceptionActionRequest("RESOLVE", "海运 + 部分先发", null));
        PurchaseExceptionView approved = service.act(APPROVER_A, id,
                new PurchaseExceptionActionRequest("APPROVE", null, null));
        assertEquals(PurchaseExceptionStatus.RESOLVED, approved.status());
        assertEquals("海运 + 部分先发", approved.resolution());
        assertTrue(approved.resolvedAt() != null);
        assertEquals(List.of("REJECTED", "APPROVED"),
                approved.approvals().stream().map(a -> a.status().name()).toList());
        assertEquals(0, service.approvalCount(APPROVER_A));
    }
}

package com.gsgd.generic_erp.service.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.gsgd.generic_erp.dto.customer.CustomerOrderView;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.pmc.AuditTaskEntity;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.purchase.LegacySyncStateRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyCustomerOrderReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyCustomerOrderRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;

/**
 * Customer order sync and the pending-audit list against a real (H2)
 * database, so the "no audit yet" JPQL is checked too.
 */
@SpringJUnitConfig(CustomerOrderSyncIntegrationTest.Config.class)
class CustomerOrderSyncIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long PMC = 42L;

    /** The rows the fake old ERP returns; tests change it between runs. */
    static class FakeReader implements LegacyCustomerOrderReader {
        final List<LegacyCustomerOrderRow> rows = new ArrayList<>();
        LocalDate lastSince;

        @Override
        public List<LegacyCustomerOrderRow> fetchDatedSince(LocalDate since, int limit) {
            lastSince = since;
            return List.copyOf(rows);
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
    @EnableJpaRepositories(basePackageClasses = { CustomerOrderRepository.class, LegacySyncStateRepository.class,
            AuditTaskRepository.class })
    @Import({ CustomerOrderSyncService.class, CustomerOrderQueryService.class })
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
            return Clock.fixed(LocalDateTime.of(2026, 11, 20, 9, 0).atZone(ZONE).toInstant(), ZONE);
        }

        @Bean
        FakeReader reader() {
            return new FakeReader();
        }

        @Bean
        LegacyErpProperties props() {
            return new LegacyErpProperties();
        }

        @Bean
        EventSink eventSink() {
            return new EventSink();
        }

        @Bean
        NotificationRecipientResolver recipientResolver() {
            NotificationRecipientResolver r = mock(NotificationRecipientResolver.class);
            when(r.usersWithRoles(any())).thenReturn(Set.of(PMC));
            return r;
        }
    }

    @Autowired
    CustomerOrderSyncService sync;
    @Autowired
    CustomerOrderQueryService query;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    LegacySyncStateRepository states;
    @Autowired
    AuditTaskRepository tasks;
    @Autowired
    FakeReader reader;
    @Autowired
    EventSink sink;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        orders.deleteAll();
        states.deleteAll();
        reader.rows.clear();
        sink.events.clear();
    }

    @Test
    void firstRunImportsQuietlyThenNewOpenOrdersNotifyPmcOnce() {
        reader.rows.add(row("E1", "SO-1", "Acme", false));
        reader.rows.add(row("E2", "SO-2", "Beta", true)); // closed in the old ERP

        var first = sync.syncNow();
        assertEquals(2, first.created());
        assertFalse(first.notified(), "the first run only imports the backlog");
        assertTrue(sink.events.isEmpty());
        assertEquals(LocalDate.of(2026, 9, 21), reader.lastSince, "60-day window");

        // Same rows again: nothing new, nothing changed.
        var again = sync.syncNow();
        assertEquals(0, again.created());
        assertEquals(0, again.updated());
        assertTrue(sink.events.isEmpty());

        // Two new orders, one of them closed: one notification about the open one.
        reader.rows.add(row("E3", "SO-3", "Gamma", false));
        reader.rows.add(row("E4", "SO-4", "Delta", true));
        var third = sync.syncNow();
        assertEquals(2, third.created());
        assertEquals(1, third.newOpen());
        assertEquals(1, sink.events.size());
        NotificationRequestedEvent e = sink.events.get(0);
        assertEquals(NotificationType.CUSTOMER_ORDER_NEW, e.type());
        assertEquals(Set.of(PMC), e.recipientIds());
        assertEquals("1 new customer order needs an audit", e.titleText().render(Locale.US));
        assertEquals("Create order audits for: SO-3 (Gamma)", e.contentText().render(Locale.US));
        assertEquals("请为以下订单创建订单评审：SO-3（Gamma）", e.contentText().render(Locale.SIMPLIFIED_CHINESE));
    }

    @Test
    void pendingListHidesClosedOrdersAndOrdersThatAlreadyHaveAnAudit() {
        reader.rows.add(row("E1", "SO-1", "Acme", false));
        reader.rows.add(row("E2", "SO-2", "Beta", true));
        reader.rows.add(row("E3", "SO-3", "Gamma", false));
        sync.syncNow();
        audit("SO-3", 1, "DRAFT");
        audit("SO-3", 2, "VERIFIED");

        var page = PageRequest.of(0, 20, Sort.by("orderDate").descending());
        List<CustomerOrderView> pending = query.pending(page).getContent();
        assertEquals(List.of("SO-1"), pending.stream().map(CustomerOrderView::orderNo).toList());
        assertTrue(pending.get(0).needsReview());
        assertEquals(1, query.pendingCount());

        CustomerOrderView so3 = query.all("so-3", page).getContent().get(0);
        assertFalse(so3.needsReview());
        assertEquals(2, so3.auditRound(), "the latest round is shown");
        assertEquals("VERIFIED", so3.auditStatus());
        CustomerOrderView so2 = query.all("beta", page).getContent().get(0);
        assertFalse(so2.needsReview(), "closed orders need no audit");
        assertNull(so2.auditStatus());
    }

    @Test
    void changesInTheOldErpAreCopied() {
        reader.rows.add(row("E1", "SO-1", "Acme", false));
        sync.syncNow();
        reader.rows.set(0, row("E1", "SO-1", "Acme Ltd", true));
        var r = sync.syncNow();
        assertEquals(1, r.updated());
        var saved = orders.findAll().get(0);
        assertEquals("Acme Ltd", saved.getCustomerName());
        assertTrue(saved.isLegacyClosed());
    }

    private static LegacyCustomerOrderRow row(String eri, String no, String customer, boolean closed) {
        return new LegacyCustomerOrderRow(eri, no, "C-" + customer, customer, LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 12, 20), "S01", 3, new BigDecimal("120"), false, closed);
    }

    private void audit(String orderNo, int round, String status) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.id = UUID.randomUUID().toString();
        t.orderNumber = orderNo;
        t.round = round;
        t.createdBy = "1";
        t.status = status;
        t.document = "{}";
        t.effectiveDueAt = Instant.now();
        tasks.save(t);
    }
}

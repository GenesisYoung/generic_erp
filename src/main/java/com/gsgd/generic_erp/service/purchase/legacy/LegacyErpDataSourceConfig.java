package com.gsgd.generic_erp.service.purchase.legacy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Read-only connection to the old ERP, created only when
 * {@code erp.legacy.enabled=true}.
 * <p>
 * The pool is deliberately NOT exposed as a {@code DataSource} bean: a second
 * DataSource bean would stop Spring Boot from auto-configuring the main MySQL
 * one. Only the reader is a bean; the pool is closed with it.
 * <p>
 * Use a SELECT-only database login for the old ERP as well. {@code readOnly}
 * here is a second safety net, not the only one.
 */
@Configuration
@ConditionalOnProperty(prefix = "erp.legacy", name = "enabled", havingValue = "true")
public class LegacyErpDataSourceConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSourceHolder legacyErpPool(LegacyErpProperties props) {
        LegacyErpProperties.Datasource ds = props.getDatasource();
        if (ds.getUrl() == null || ds.getUrl().isBlank()) {
            throw new IllegalStateException("erp.legacy.enabled is true but erp.legacy.datasource.url is empty");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("legacy-erp");
        config.setJdbcUrl(ds.getUrl());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setDriverClassName(ds.getDriverClassName());
        config.setMaximumPoolSize(ds.getMaximumPoolSize());
        config.setMinimumIdle(0);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        // Do not block application start-up if the old ERP is unreachable.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSourceHolder(new HikariDataSource(config));
    }

    @Bean
    public LegacyPurchaseOrderReader legacyPurchaseOrderReader(HikariDataSourceHolder pool, LegacyErpProperties props) {
        JdbcTemplate template = new JdbcTemplate(pool.dataSource());
        template.setQueryTimeout(props.getDatasource().getQueryTimeoutSeconds());
        return new JdbcLegacyPurchaseOrderReader(template, props.getPurchaseOrder().getQuery());
    }

    /** Only when a line query is configured; without it every order keeps one follow-up task. */
    @Bean
    @org.springframework.context.annotation.Conditional(LineQuerySet.class)
    public LegacyPurchaseOrderLineReader legacyPurchaseOrderLineReader(HikariDataSourceHolder pool,
            LegacyErpProperties props) {
        JdbcTemplate template = new JdbcTemplate(pool.dataSource());
        template.setQueryTimeout(props.getDatasource().getQueryTimeoutSeconds());
        return new JdbcLegacyPurchaseOrderLineReader(template, props.getPurchaseOrder().getLineQuery());
    }

    @Bean
    public LegacyCustomerOrderReader legacyCustomerOrderReader(HikariDataSourceHolder pool,
            LegacyErpProperties props) {
        JdbcTemplate template = new JdbcTemplate(pool.dataSource());
        template.setQueryTimeout(props.getDatasource().getQueryTimeoutSeconds());
        return new JdbcLegacyCustomerOrderReader(template, props.getCustomerOrder().getQuery(),
                props.getCustomerOrder().getPoLinkQuery());
    }

    @Bean
    public LegacyPlacementSourceReader legacyPlacementSourceReader(HikariDataSourceHolder pool,
            LegacyErpProperties props) {
        JdbcTemplate template = new JdbcTemplate(pool.dataSource());
        template.setQueryTimeout(props.getDatasource().getQueryTimeoutSeconds());
        return new JdbcLegacyPlacementSourceReader(template, props.getPlacementSource().getSampleQuery(),
                props.getPlacementSource().getProductionQuery());
    }

    @Bean
    public LegacyQuotationReader legacyQuotationReader(HikariDataSourceHolder pool, LegacyErpProperties props) {
        JdbcTemplate template = new JdbcTemplate(pool.dataSource());
        template.setQueryTimeout(props.getDatasource().getQueryTimeoutSeconds());
        LegacyErpProperties.QuotationQueries q = props.getQuotation();
        return new JdbcLegacyQuotationReader(template, q.getOrderQuery(), q.getBomLineQuery(), q.getPriceQuery());
    }

    /** True when erp.legacy.purchase-order.line-query has text (a SpEL check would break on quotes in the SQL). */
    static class LineQuerySet implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            String q = context.getEnvironment().getProperty("erp.legacy.purchase-order.line-query");
            return q != null && !q.isBlank();
        }
    }

    /** Wraps the pool so it is not a DataSource bean (see class comment). */
    public record HikariDataSourceHolder(HikariDataSource dataSource) implements AutoCloseable {
        @Override
        public void close() {
            dataSource.close();
        }
    }
}

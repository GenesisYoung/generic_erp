package com.gsgd.generic_erp.service.purchase.legacy;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.gsgd.generic_erp.enums.PurchaseOrderStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * Settings for reading the old ERP (高格 VEPRO on SQL Server), bound from
 * {@code erp.legacy.*}. Everything is off until {@code enabled} is true, so the
 * application starts normally without the old ERP.
 * <p>
 * Credentials must come from environment variables, never from the repository.
 */
@Component
@ConfigurationProperties(prefix = "erp.legacy")
@Getter
@Setter
public class LegacyErpProperties {

    private boolean enabled = false;

    /** Written to purchase_order_tb.source_system. */
    private String sourceSystem = "LEGACY_ERP";

    private Datasource datasource = new Datasource();

    private PurchaseOrder purchaseOrder = new PurchaseOrder();

    @Getter
    @Setter
    public static class Datasource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        private int maximumPoolSize = 2;
        private int queryTimeoutSeconds = 60;
    }

    @Getter
    @Setter
    public static class PurchaseOrder {
        /**
         * SELECT that returns purchase orders changed after the named parameter
         * {@code :since}, ordered by update time. Required column aliases:
         * po_no, supplier_code, supplier_name, buyer_code, order_date,
         * expected_date, status, updated_at. Filled in once the old ERP schema
         * is known.
         */
        private String query;

        /** Cron for the sync job, in the business zone. Default: every 15 minutes. */
        private String syncCron = "0 */15 * * * *";

        /** On the very first run, read orders changed in the last N days. */
        private int initialLookbackDays = 60;

        /** Maximum rows per run; the next run continues from the last update time. */
        private int batchLimit = 5000;

        /**
         * Raw old-ERP status value to this system's status. A status that is not
         * listed counts as PLACED (still needs follow-up).
         */
        private Map<String, PurchaseOrderStatus> statusMap = new HashMap<>();
    }
}

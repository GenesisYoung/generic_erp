package com.gsgd.generic_erp.service.purchase.legacy;

import java.util.HashMap;
import java.util.List;
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

    private CustomerOrder customerOrder = new CustomerOrder();

    private PlacementSourceQueries placementSource = new PlacementSourceQueries();

    private QuotationQueries quotation = new QuotationQueries();

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

        /**
         * SELECT that returns the lines of the orders whose numbers are in the
         * named list parameter {@code :poNos}. Required column aliases: po_no,
         * line_no, material_code, material_name, plan_type, plan_type_name,
         * qty, received_qty. {@code plan_type} decides which follow-up task a
         * line belongs to (see the plan groups page). Empty = lines are not
         * synced and every order keeps one follow-up task.
         */
        private String lineQuery;

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

    @Getter
    @Setter
    public static class CustomerOrder {
        /**
         * SELECT that returns customer order headers dated on or after the named
         * parameter {@code :since} (a date). Required column aliases: legacy_id,
         * order_no, customer_code, customer_name, order_date, delivery_date,
         * sales_code, line_count, total_qty, legacy_audited (0/1), closed (0/1).
         */
        private String query;

        /** Cron for the sync job, in the business zone. Default: every 15 minutes. */
        private String syncCron = "0 */15 * * * *";

        /**
         * Every run re-reads orders dated in the last N days and compares them by
         * old-ERP row id. The old ERP leaves the change time empty on about a
         * quarter of its orders, so a time-based "changed since" is not reliable.
         */
        private int lookbackDays = 60;

        /**
         * SELECT that links purchase orders dated on or after {@code :since} to
         * the customer order lines they buy for. Required aliases:
         * customer_order_id, customer_order_no, customer_line_id, po_no, po_date.
         */
        private String poLinkQuery;

        /** Purchase orders dated in the last N days are re-read for links on every run. */
        private int poLinkLookbackDays = 60;

        /** Maximum link rows per run (about 3,000 PO lines in 60 days). */
        private int poLinkBatchLimit = 20000;

        /** Maximum rows per run (about 35 orders a month are entered). */
        private int batchLimit = 2000;

        /** Users with these roles are told about new orders. */
        private List<String> notifyRoles = List.of("pmc");
    }

    /**
     * Documents that decide purchase placement tasks (采购下单任务). Each query
     * returns document lines dated on or after the named parameter
     * {@code :since} (a date). Required column aliases: order_no, source_no,
     * source_date, confirmed (0/1), note, first_po_no. A blank query
     * switches that order type off.
     */
    @Getter
    @Setter
    public static class PlacementSourceQueries {
        /** Purchase applications (采购申请) of sample orders (YP...). */
        private String sampleQuery;

        /** Purchase plans (采购计划) of production orders (P...). */
        private String productionQuery;

        /** Documents dated in the last N days are re-read on every run. */
        private int lookbackDays = 30;

        /** Maximum rows per order type per run. */
        private int batchLimit = 20000;
    }

    /**
     * Data for purchase quotations (报价方案): orders that can be quoted, their
     * BOM lines and the supplier prices of the purchase strategy (采购策略).
     * A blank query switches the quotation pages to "old ERP not connected".
     */
    @Getter
    @Setter
    public static class QuotationQueries {
        /**
         * Customer order lines whose purchase plan exists and has no purchase
         * order yet, dated on or after {@code :since}. Required aliases:
         * order_no, customer_code, customer_name, order_date, delivery_date,
         * plan_no, plan_date, line_id, product_code, product_name, spec, qty,
         * unit, product_bom_code, packaging_bom_code.
         */
        private String orderQuery;

        /**
         * BOM lines of one order ({@code :orderNo}). Required aliases:
         * bom_kind (PRODUCT / PACKAGING, or empty = decide by material class),
         * bom_code, line_id, product_code, order_qty, line_no, bom_level,
         * material_code, material_name, spec, unit, material_class,
         * qty_per_unit, leaf (0/1), base_price.
         */
        private String bomLineQuery;

        /**
         * Valid supplier prices of the materials in the named list parameter
         * {@code :materialCodes}. Required aliases: material_code,
         * supplier_code, supplier_name, price, tax_rate, currency, valid_from,
         * valid_to.
         */
        private String priceQuery;

        /** Orders whose purchase plan is dated in the last N days are listed. */
        private int lookbackDays = 90;

        /** Maximum order lines read for the list. */
        private int batchLimit = 5000;
    }
}

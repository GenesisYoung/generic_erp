package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One line of a purchase order, copied from the old ERP by the sync job
 * (ORDSPHS2). The line's {@code planTypeCode} decides which follow-up task
 * it belongs to (see {@link FollowUpPlanGroup}). Maps to table:
 * purchase_order_line_tb.
 */
@Entity
@Table(name = "purchase_order_line_tb", uniqueConstraints = @UniqueConstraint(name = "uk_po_line", columnNames = {
        "purchase_order_id", "line_no" }), indexes = @Index(name = "idx_po_line_plan_type", columnList = "plan_type_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    /** Line number in the source order (ORDSPHS2.SERIAL). */
    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "material_code", length = 60)
    private String materialCode;

    @Column(name = "material_name", length = 255)
    private String materialName;

    /** The purchase plan type of the line, as the sync query returns it; null = unknown. */
    @Column(name = "plan_type_code", length = 50)
    private String planTypeCode;

    @Column(name = "qty")
    private Double qty;

    @Column(name = "received_qty")
    private Double receivedQty;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /** True while the line still waits for goods (nothing or less than ordered received). */
    public boolean isOpen() {
        double ordered = qty == null ? 0 : qty;
        double received = receivedQty == null ? 0 : receivedQty;
        return ordered <= 0 || received < ordered;
    }
}

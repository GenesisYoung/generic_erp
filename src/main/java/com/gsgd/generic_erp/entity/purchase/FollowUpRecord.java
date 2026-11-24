package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.MaterialStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One follow-up a purchaser actually did on a purchase order: what they found
 * out from the supplier. A record saved inside an OPEN checkpoint window
 * completes that checkpoint; extra records are kept but complete nothing.
 * Maps to table: po_followup_record_tb.
 */
@Entity
@Table(name = "po_followup_record_tb", indexes = {
        @Index(name = "idx_followup_record_po", columnList = "purchase_order_id, followed_up_at"),
        @Index(name = "idx_followup_record_user", columnList = "recorded_by, followed_up_at") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    /** The checkpoint this record completed; null for an extra follow-up. */
    @Column(name = "checkpoint_id")
    private Long checkpointId;

    /** Plan group of the follow-up task this record belongs to; null = the main task. */
    @Column(name = "plan_group_id")
    private Long planGroupId;

    /** user_tb.id of the user who saved the record. */
    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_status", length = 16, nullable = false)
    private MaterialStatus materialStatus;

    /** Delivery date the supplier promised during this follow-up, if any. */
    @Column(name = "supplier_promised_date")
    private LocalDate supplierPromisedDate;

    /** True when the purchaser found that the supplier never got or lost the order. */
    @Column(name = "missing_order_found", nullable = false)
    private boolean missingOrderFound;

    @Column(name = "note", length = 500)
    private String note;

    /** Server time (business zone) when the follow-up was saved; it decides the window. */
    @Column(name = "followed_up_at", nullable = false)
    private LocalDateTime followedUpAt;
}

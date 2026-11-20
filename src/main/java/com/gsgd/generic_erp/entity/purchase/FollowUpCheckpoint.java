package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.FollowUpStage;

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
 * One required follow-up window of a purchase order: at least one follow-up
 * record must be saved between {@code windowStart} and {@code windowEnd}
 * (inclusive calendar dates in the business time zone).
 * Maps to table: po_followup_checkpoint_tb.
 */
@Entity
@Table(name = "po_followup_checkpoint_tb", indexes = {
        @Index(name = "idx_checkpoint_po", columnList = "purchase_order_id, seq"),
        @Index(name = "idx_checkpoint_status_end", columnList = "status, window_end") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 16, nullable = false)
    private FollowUpStage stage;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private CheckpointStatus status;

    /** The follow-up record that completed this window (next step). */
    @Column(name = "done_record_id")
    private Long doneRecordId;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

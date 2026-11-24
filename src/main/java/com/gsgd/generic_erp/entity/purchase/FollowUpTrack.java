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
 * The separate follow-up task series of one plan group inside one purchase
 * order. Its checkpoints carry the same {@code planGroupId}. The order's main
 * series (lines in no separate group) has no row here: it uses
 * {@code purchase_order_tb.followup_rule_id}. Maps to table:
 * po_followup_track_tb.
 */
@Entity
@Table(name = "po_followup_track_tb", uniqueConstraints = @UniqueConstraint(name = "uk_track_po_group", columnNames = {
        "purchase_order_id", "plan_group_id" }), indexes = @Index(name = "idx_track_group", columnList = "plan_group_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "plan_group_id", nullable = false)
    private Long planGroupId;

    @Column(name = "followup_rule_id")
    private Long followupRuleId;

    /** False once the group no longer has open lines in the order, or stopped being separate. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

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
 * A named set of purchase plan types that count as the same plan (跟进计划分组).
 * When {@code separate} is true, the lines of an order whose plan type is in
 * this group get their own follow-up task series (a {@link FollowUpTrack}),
 * planned from {@code followupRuleId} or the default template. When it is
 * false, they stay in the order's main follow-up task. Maps to table:
 * followup_plan_group_tb.
 */
@Entity
@Table(name = "followup_plan_group_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpPlanGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "group_name", length = 100, nullable = false)
    private String groupName;

    /** True: its lines are followed up as their own task. False: they stay in the main task. */
    @Column(name = "is_separate", nullable = false)
    private boolean separate;

    /** Template for its own task; null = the default template. */
    @Column(name = "followup_rule_id")
    private Long followupRuleId;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

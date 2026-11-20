package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A named follow-up rule. Exactly one active rule is the default; a purchase
 * order may point to a different rule (decided 2026-11-20). The stages live in
 * {@link FollowUpRuleStage}. Maps to table: followup_rule_tb.
 */
@Entity
@Table(name = "followup_rule_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rule_name", length = 100, nullable = false)
    private String ruleName;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRule;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** Days per OVERDUE checkpoint after the last stage; null = no overdue follow-up. */
    @Column(name = "overdue_period_days")
    private Integer overduePeriodDays;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

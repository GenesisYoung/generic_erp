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
 * A named follow-up rule, shown to users as a follow-up template. Exactly one active rule is the default; a purchase
 * order may point to a different rule (decided 2026-11-20). A rule is either
 * stage-based ({@link FollowUpRuleStage}) or day-based ({@link FollowUpRuleDay}:
 * one notification task per chosen day); when it has days, the days win. Maps to table: followup_rule_tb.
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

    /**
     * Expected production period in calendar days after the order date. Used
     * by day-based templates: the last notification task runs until this day,
     * and OVERDUE follow-up starts after it.
     */
    @Column(name = "production_days")
    private Integer productionDays;

    /** Which materials the template is meant for (free text shown to users). */
    @Column(name = "material_note", length = 200)
    private String materialNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

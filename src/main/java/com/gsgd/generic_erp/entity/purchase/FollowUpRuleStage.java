package com.gsgd.generic_erp.entity.purchase;

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
 * One stage of a follow-up rule: from {@code startDay} to {@code endDay}
 * (1-based calendar days after the order date), {@code checksPerPeriod}
 * checks every {@code periodDays} days. Maps to table: followup_rule_stage_tb.
 */
@Entity
@Table(name = "followup_rule_stage_tb", indexes = @Index(name = "idx_rule_stage_rule", columnList = "rule_id, seq"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpRuleStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 16, nullable = false)
    private FollowUpStage stage;

    @Column(name = "start_day", nullable = false)
    private Integer startDay;

    @Column(name = "end_day", nullable = false)
    private Integer endDay;

    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Column(name = "checks_per_period", nullable = false)
    private Integer checksPerPeriod;
}

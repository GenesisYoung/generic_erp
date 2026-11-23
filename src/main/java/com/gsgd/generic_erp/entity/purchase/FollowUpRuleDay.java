package com.gsgd.generic_erp.entity.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * One notification day of a day-based follow-up template: {@code dayNo}
 * calendar days after the order date (1 = the day after). Each day becomes
 * its own follow-up task. Maps to table: followup_rule_day_tb.
 */
@Entity
@Table(name = "followup_rule_day_tb", indexes = @Index(name = "idx_rule_day_rule", columnList = "rule_id, day_no"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpRuleDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "day_no", nullable = false)
    private Integer dayNo;
}

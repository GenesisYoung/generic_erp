package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.entity.purchase.FollowUpRuleStage;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleStageRepository;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.StageDef;

import lombok.RequiredArgsConstructor;

/**
 * Loads follow-up rules as {@link StageDef} lists. If the database has no
 * default rule yet, the built-in one (the 2026-11-20 business rules) is
 * stored on first use, so a fresh database works without running seed SQL.
 */
@Service
@RequiredArgsConstructor
public class FollowUpRuleService {

    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpRuleStageRepository stageRepository;
    private final Clock clock;

    /** Id of the active default rule, created if missing. */
    @Transactional
    public Long defaultRuleId() {
        return ruleRepository.findFirstByDefaultRuleTrueAndActiveTrueOrderByIdAsc()
                .map(FollowUpRule::getId)
                .orElseGet(this::createDefaultRule);
    }

    /** Stages of a rule; the built-in default when the rule has no stages. */
    public List<StageDef> stagesOf(Long ruleId) {
        List<StageDef> stages = ruleId == null ? List.of()
                : stageRepository.findByRuleIdOrderBySeqAsc(ruleId).stream()
                        .map(s -> new StageDef(s.getStage(), s.getStartDay(), s.getEndDay(), s.getPeriodDays(),
                                s.getChecksPerPeriod()))
                        .toList();
        return stages.isEmpty() ? FollowUpPlanBuilder.defaultStages() : stages;
    }

    private Long createDefaultRule() {
        FollowUpRule rule = ruleRepository.save(FollowUpRule.builder()
                .ruleName("Default: 2x/week weeks 1-2, every 2 days week 3, daily week 4")
                .defaultRule(true)
                .active(true)
                .overduePeriodDays(1)
                .createdAt(LocalDateTime.now(clock))
                .build());
        List<StageDef> defaults = FollowUpPlanBuilder.defaultStages();
        for (int i = 0; i < defaults.size(); i++) {
            StageDef d = defaults.get(i);
            stageRepository.save(FollowUpRuleStage.builder()
                    .ruleId(rule.getId())
                    .seq(i + 1)
                    .stage(d.stage())
                    .startDay(d.startDay())
                    .endDay(d.endDay())
                    .periodDays(d.periodDays())
                    .checksPerPeriod(d.checksPerPeriod())
                    .build());
        }
        return rule.getId();
    }
}

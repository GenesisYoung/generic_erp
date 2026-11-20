package com.gsgd.generic_erp.repository.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.FollowUpRuleStage;

public interface FollowUpRuleStageRepository extends JpaRepository<FollowUpRuleStage, Long> {

    List<FollowUpRuleStage> findByRuleIdOrderBySeqAsc(Long ruleId);
}

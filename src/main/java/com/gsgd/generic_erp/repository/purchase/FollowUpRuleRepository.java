package com.gsgd.generic_erp.repository.purchase;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.FollowUpRule;

public interface FollowUpRuleRepository extends JpaRepository<FollowUpRule, Long> {

    Optional<FollowUpRule> findFirstByDefaultRuleTrueAndActiveTrueOrderByIdAsc();
}

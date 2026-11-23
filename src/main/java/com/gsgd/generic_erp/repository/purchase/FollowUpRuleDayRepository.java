package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpRuleDay;

public interface FollowUpRuleDayRepository extends JpaRepository<FollowUpRuleDay, Long> {

    List<FollowUpRuleDay> findByRuleIdOrderByDayNoAsc(Long ruleId);

    List<FollowUpRuleDay> findByRuleIdIn(Collection<Long> ruleIds);

    @Modifying
    @Query("delete from FollowUpRuleDay d where d.ruleId = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);
}

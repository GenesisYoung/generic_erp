package com.gsgd.generic_erp.repository.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.FollowUpPlanGroup;

public interface FollowUpPlanGroupRepository extends JpaRepository<FollowUpPlanGroup, Long> {

    List<FollowUpPlanGroup> findByActiveTrueOrderByGroupNameAsc();
}

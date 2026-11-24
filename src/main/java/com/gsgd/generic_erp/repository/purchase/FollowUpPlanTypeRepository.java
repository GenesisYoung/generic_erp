package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpPlanType;

public interface FollowUpPlanTypeRepository extends JpaRepository<FollowUpPlanType, Long> {

    Optional<FollowUpPlanType> findByTypeCode(String typeCode);

    List<FollowUpPlanType> findByTypeCodeIn(Collection<String> typeCodes);

    List<FollowUpPlanType> findAllByOrderByTypeCodeAsc();

    @Modifying
    @Query("update FollowUpPlanType t set t.groupId = null where t.groupId = :groupId")
    int ungroup(@Param("groupId") Long groupId);
}

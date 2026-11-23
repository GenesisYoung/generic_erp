package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;

public interface FollowUpRuleRepository extends JpaRepository<FollowUpRule, Long> {

    Optional<FollowUpRule> findFirstByDefaultRuleTrueAndActiveTrueOrderByIdAsc();

    List<FollowUpRule> findByActiveTrueOrderByDefaultRuleDescRuleNameAsc();

    /** Active purchase orders per rule, for "used by N orders" on the template page. */
    @Query("""
            select po.followupRuleId, count(po) from PurchaseOrder po
            where po.status in :statuses and po.followupRuleId is not null group by po.followupRuleId""")
    List<Object[]> countOrdersByRule(@Param("statuses") Collection<PurchaseOrderStatus> statuses);
}

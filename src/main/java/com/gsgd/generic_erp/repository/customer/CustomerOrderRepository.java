package com.gsgd.generic_erp.repository.customer;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.customer.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByLegacyIdIn(Collection<String> legacyIds);

    /** Open orders with no order audit yet (any round), matching on the order number. */
    @Query("""
            select c from CustomerOrder c
            where c.legacyClosed = false
              and not exists (select 1 from AuditTaskEntity t where t.orderNumber = c.orderNo)
            """)
    Page<CustomerOrder> findPendingReview(Pageable pageable);

    @Query("""
            select count(c) from CustomerOrder c
            where c.legacyClosed = false
              and not exists (select 1 from AuditTaskEntity t where t.orderNumber = c.orderNo)
            """)
    long countPendingReview();

    /** Every order, optionally narrowed to an order number or customer containing {@code q}. */
    @Query("""
            select c from CustomerOrder c
            where :q is null
               or lower(c.orderNo) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.customerName, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.customerCode, '')) like lower(concat('%', :q, '%'))
            """)
    Page<CustomerOrder> search(@Param("q") String q, Pageable pageable);
}

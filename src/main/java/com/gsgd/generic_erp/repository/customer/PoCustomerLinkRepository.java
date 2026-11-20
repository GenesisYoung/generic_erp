package com.gsgd.generic_erp.repository.customer;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.customer.PoCustomerLink;

public interface PoCustomerLinkRepository extends JpaRepository<PoCustomerLink, Long> {

    List<PoCustomerLink> findByCustomerLineLegacyIdIn(Collection<String> lineIds);

    List<PoCustomerLink> findByCustomerOrderNoIn(Collection<String> orderNos);
}

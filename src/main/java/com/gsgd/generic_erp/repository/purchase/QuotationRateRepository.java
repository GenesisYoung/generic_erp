package com.gsgd.generic_erp.repository.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.QuotationRate;

public interface QuotationRateRepository extends JpaRepository<QuotationRate, Long> {

    List<QuotationRate> findAllByOrderByKindAscSortOrderAscIdAsc();
}

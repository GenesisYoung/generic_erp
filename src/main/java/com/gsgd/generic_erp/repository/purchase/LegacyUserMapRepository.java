package com.gsgd.generic_erp.repository.purchase;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.LegacyUserMap;

public interface LegacyUserMapRepository extends JpaRepository<LegacyUserMap, Long> {

    Optional<LegacyUserMap> findBySourceSystemAndLegacyCode(String sourceSystem, String legacyCode);
}

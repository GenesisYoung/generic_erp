package com.gsgd.generic_erp.repository.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.LegacySyncState;

public interface LegacySyncStateRepository extends JpaRepository<LegacySyncState, String> {
}

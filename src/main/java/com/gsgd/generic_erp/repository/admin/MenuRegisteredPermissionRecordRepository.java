package com.gsgd.generic_erp.repository.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.auth.MenuRegisteredPermissionsRecord;

public interface MenuRegisteredPermissionRecordRepository extends JpaRepository<MenuRegisteredPermissionsRecord, Long>,
        JpaSpecificationExecutor<MenuRegisteredPermissionsRecord> {
    List<MenuRegisteredPermissionsRecord> findByMenuId(Long menuId);
}

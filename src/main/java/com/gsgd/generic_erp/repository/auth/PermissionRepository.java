package com.gsgd.generic_erp.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.auth.Permission;

/**
 * Repository for the Permission entity (permission_tb).
 */
public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    boolean existsByPermissionName(String permissionName);
}

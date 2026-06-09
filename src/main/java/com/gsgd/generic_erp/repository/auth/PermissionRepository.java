package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gsgd.generic_erp.entity.auth.Permission;

/**
 * Repository for the Permission entity (permission_tb).
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionName(String permissionName);

    boolean existsByPermissionName(String permissionName);
}

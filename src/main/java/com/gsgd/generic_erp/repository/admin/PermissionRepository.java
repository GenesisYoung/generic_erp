package com.gsgd.generic_erp.repository.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.entity.auth.Permission;

/**
 * Repository for the Permission entity (permission_tb).
 */
public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    boolean existsByPermissionCode(String permissionName);

    @Transactional
    @Modifying
    void deleteByPermissionCode(String id);

    @NativeQuery("SELECT * FROM permission_tb p WHERE p.permission_code LIKE ?1")
    List<Permission> findChildrenPermission(String ele);

    @Transactional
    @Modifying
    @NativeQuery("UPDATE permission_tb p SET p.is_enabled=0 WHERE p.id = ?1")
    void disable(Long id);

}

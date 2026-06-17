package com.gsgd.generic_erp.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.gsgd.generic_erp.entity.auth.ActionPermission;

import jakarta.transaction.Transactional;

/**
 * Repository for the ActionPermission join entity (action_permission_tb).
 */
public interface ActionPermissionRepository extends JpaRepository<ActionPermission, Long> {

    List<ActionPermission> findByPermissionId(Long permissionId);

    List<ActionPermission> findByActionId(Long actionId);

    boolean existsByPermissionIdAndActionId(Long permissionId, Long actionId);

    @Modifying
    @Transactional
    void deleteByPermissionId(Long permissionId);

    @Modifying
    @Transactional
    void deleteByActionId(Long actionId);

    @Modifying
    @Transactional
    void deleteByPermissionIdAndActionId(Long permissionId, Long actionId);
}

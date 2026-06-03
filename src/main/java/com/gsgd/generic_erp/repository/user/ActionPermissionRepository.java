package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.ActionPermission;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the ActionPermission join entity (action_permission_tb).
 */
@Repository
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

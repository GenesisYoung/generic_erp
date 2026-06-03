package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.RolePermission;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the RolePermission join entity (role_permission_tb).
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByPermissionId(Long permissionId);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Modifying
    @Transactional
    void deleteByRoleId(Long roleId);

    @Modifying
    @Transactional
    void deleteByPermissionId(Long permissionId);

    @Modifying
    @Transactional
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}

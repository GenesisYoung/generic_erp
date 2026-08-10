package com.gsgd.generic_erp.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.gsgd.generic_erp.entity.auth.RoleDepartment;

import jakarta.transaction.Transactional;

/**
 * Repository for the RoleDepartment join entity (role_department_tb).
 */
public interface RoleDepartmentRepository extends JpaRepository<RoleDepartment, Long> {

    List<RoleDepartment> findByDepartmentId(Long departmentId);

    List<RoleDepartment> findByRoleId(Long roleId);

    boolean existsByRoleIdAndDepartmentId(Long roleId, Long departmentId);

    @Modifying
    @Transactional
    void deleteByRoleId(Long roleId);

    @Modifying
    @Transactional
    void deleteByDepartmentId(Long departmentId);

    @Modifying
    @Transactional
    void deleteByRoleIdAndDepartmentId(Long roleId, Long departmentId);
}

package com.gsgd.generic_erp.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.gsgd.generic_erp.entity.auth.UserDepartment;

import jakarta.transaction.Transactional;

/**
 * Repository for the UserDepartment join entity (user_departments_tb).
 */
public interface UserDepartmentRepository extends JpaRepository<UserDepartment, Long> {

    List<UserDepartment> findByUserId(Long userId);

    List<UserDepartment> findByDeptId(Long deptId);

    boolean existsByUserIdAndDeptId(Long userId, Long deptId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByDeptId(Long deptId);

    @Modifying
    @Transactional
    void deleteByUserIdAndDeptId(Long userId, Long deptId);
}

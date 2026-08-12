package com.gsgd.generic_erp.repository.auth;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;

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

    @Modifying
    @Transactional
    @NativeQuery("DELETE FROM user_departments_tb t WHERE t.userId=?1 AND t.deptId IN ?2")
    void deleteThroughUserIdAndDeptIds(Long userId, Set<Long> toRemove);
}

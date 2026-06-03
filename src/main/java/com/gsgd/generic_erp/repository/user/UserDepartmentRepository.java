package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.UserDepartment;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the UserDepartment join entity (user_departments_tb).
 */
@Repository
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

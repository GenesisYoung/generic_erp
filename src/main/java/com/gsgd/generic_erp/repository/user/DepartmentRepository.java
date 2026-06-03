package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the Department entity (department_tb).
 *
 * Departments form a tree via parent_id (null = root).
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDeptName(String deptName);

    boolean existsByDeptName(String deptName);

    /** All root departments (parent_id IS NULL). */
    List<Department> findByParentIdIsNull();

    /** All direct children of the given department. */
    List<Department> findByParentId(Long parentId);
}

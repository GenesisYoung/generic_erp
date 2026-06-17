package com.gsgd.generic_erp.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.Department;

/**
 * Repository for the Department entity (department_tb).
 *
 * Departments form a tree via parent_id (null = root).
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDeptName(String deptName);

    boolean existsByDeptName(String deptName);

    /** All root departments (parent_id IS NULL). */
    List<Department> findByParentIdIsNull();

    /** All direct children of the given department. */
    List<Department> findByParentId(Long parentId);
}

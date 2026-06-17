package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.Role;

/**
 * Repository for the Role entity (role_tb).
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);
}

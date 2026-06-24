package com.gsgd.generic_erp.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

import com.gsgd.generic_erp.entity.auth.Role;

/**
 * Repository for the Role entity (role_tb).
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);

    @NativeQuery(value = "SELECT r.val FROM role_tb r WHERE r.id IN (SELECT u.role_id FROM user_roles_tb u WHERE u.user_id=?1)")
    List<String> findByUserId(Long id);
}

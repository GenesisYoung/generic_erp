package com.gsgd.generic_erp.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.gsgd.generic_erp.entity.auth.UserRole;

import jakarta.transaction.Transactional;

/**
 * Repository for the UserRole join entity (user_roles_tb).
 *
 * Used to assign / revoke roles for a user, and to fetch the role IDs
 * for a given user during authorization.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByRoleId(Long roleId);

    @Modifying
    @Transactional
    void deleteByUserIdAndRoleId(Long userId, Long roleId);
}

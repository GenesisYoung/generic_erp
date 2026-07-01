package com.gsgd.generic_erp.repository.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.auth.UserRole;

import jakarta.transaction.Transactional;

/**
 * Repository for the UserRole join entity (user_roles_tb).
 *
 * Used to assign / revoke roles for a user, and to fetch the role IDs
 * for a given user during authorization.
 */
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

    @NativeQuery("SELECT * FROM user_roles_tb ur WHERE ur.role_id=?1 AND ur.user_id=?2")
    UserRole findByBothRoleAndUser(Long roleId, Long userId);

    // Spring Data derives a "WHERE user_id = ? AND role_id IN (?, ?, ...)" delete
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId AND ur.roleId IN :roleIds")
    void deleteByUserIdAndRoleIdIn(@Param("userId") Long userId,
            @Param("roleIds") Collection<Long> roleIds);

}

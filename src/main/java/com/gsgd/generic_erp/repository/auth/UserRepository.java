package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.gsgd.generic_erp.entity.auth.User;

/**
 * Repository for the User entity (user_tb).
 *
 * Primary use case: authentication — look up by username during login.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Used by the authentication provider during login. */
    Optional<User> findByUsername(String username);

    /** Used during registration / admin-create to reject duplicates. */
    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.isEnabled = :isEnabled WHERE u.id = :id")
    void updateIsEnabled(Long id, Byte isEnabled);
}

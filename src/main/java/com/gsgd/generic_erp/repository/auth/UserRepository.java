package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.User;

/**
 * Repository for the User entity (user_tb).
 *
 * Primary use case: authentication — look up by username during login.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by the authentication provider during login. */
    Optional<User> findByUsername(String username);

    /** Used during registration / admin-create to reject duplicates. */
    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

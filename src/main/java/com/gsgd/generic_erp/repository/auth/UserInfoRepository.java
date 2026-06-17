package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.UserInfo;

/**
 * Repository for the UserInfo entity (user_info_tb).
 *
 * UserInfo is a 1-to-1 extension of User, keyed by user_id.
 */
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    Optional<UserInfo> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

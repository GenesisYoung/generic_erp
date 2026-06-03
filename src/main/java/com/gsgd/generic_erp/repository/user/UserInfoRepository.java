package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the UserInfo entity (user_info_tb).
 *
 * UserInfo is a 1-to-1 extension of User, keyed by user_id.
 */
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    Optional<UserInfo> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

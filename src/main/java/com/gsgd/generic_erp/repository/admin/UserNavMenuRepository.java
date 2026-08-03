package com.gsgd.generic_erp.repository.admin;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.auth.UserNavMenu;

import jakarta.transaction.Transactional;

/** Repository for direct user-to-menu grants ({@code user_nav_menu}). */
public interface UserNavMenuRepository extends JpaRepository<UserNavMenu, Long>, JpaSpecificationExecutor<UserNavMenu> {

    /** All enabled menu grants for one user. */
    @NativeQuery("SELECT * FROM user_nav_menu AS m WHERE m.user_id = ?1 and m.is_enabled = 1")
    List<UserNavMenu> findByUserId(Long id);

    /** Removes every user's grant for the given menus; used before menu deletion. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @NativeQuery("DELETE FROM user_nav_menu WHERE nav_id IN (:navIds)")
    int deleteByNavIds(@Param("navIds") Collection<Long> navIds);

    /** Every enabled grant for a menu, across all users. */
    List<UserNavMenu> findByNavIdAndIsEnabledTrue(Long navId);

    /** Every grant (enabled or not) a user holds for a menu — the toggle target set. */
    List<UserNavMenu> findByNavIdAndUserId(Long navId, Long userId);
}
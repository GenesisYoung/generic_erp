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

public interface UserNavMenuRepository extends JpaRepository<UserNavMenu, Long>, JpaSpecificationExecutor<UserNavMenu> {

    @NativeQuery("SELECT * FROM user_nav_menu AS m WHERE m.user_id = ?1 and m.is_enabled = 1")
    List<UserNavMenu> findByUserId(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @NativeQuery("DELETE FROM user_nav_menu WHERE nav_id IN (:navIds)")
    int deleteByNavIds(@Param("navIds") Collection<Long> navIds);

    /** Every enabled grant for a menu, across all users and permissions. */
    List<UserNavMenu> findByNavIdAndIsEnabledTrue(Long navId);

    /** Every grant (enabled or not) a user holds for a menu — the toggle target set. */
    List<UserNavMenu> findByNavIdAndUserId(Long navId, Long userId);

    /** Every enabled grant a user holds for a menu, used to derive granted permissions. */
    List<UserNavMenu> findByNavIdAndUserIdAndIsEnabledTrue(Long navId, Long userId);

    /**
     * The specific grant row for a (menu, user, permission) triple.
     * Passing permissionId = null resolves to the baseline "has access, no
     * specific permission" row (Spring Data rewrites the equality check to
     * IS NULL for a null bind value).
     */
    List<UserNavMenu> findByNavIdAndUserIdAndPermissionId(Long navId, Long userId, Long permissionId);
}
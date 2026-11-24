package com.gsgd.generic_erp.repository.admin;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.entity.auth.Permission;

/**
 * Repository for the Permission entity (permission_tb).
 */
public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    boolean existsByPermissionCode(String permissionName);

    @Transactional
    @Modifying
    void deleteByPermissionCode(String id);

    @NativeQuery("SELECT * FROM permission_tb p WHERE p.permission_code LIKE ?1")
    List<Permission> findChildrenPermission(String ele);

    @Transactional
    @Modifying
    @NativeQuery("UPDATE permission_tb p SET p.is_enabled=0 WHERE p.id = ?1")
    void disable(Long id);

    @NativeQuery("SELECT * FROM permission_tb p WHERE p.is_enabled = 1")
    List<Permission> findExists();

    /**
     * Ids of every role a user holds: the roles bound to the user directly
     * ({@code user_roles_tb}, used for ROOT / ADMIN) and the one role of each
     * department the user belongs to ({@code role_department_tb}). The
     * department role is where a user's default permissions come from.
     */
    String USER_ROLE_IDS = """
            SELECT ur.role_id FROM user_roles_tb ur WHERE ur.user_id = ?1
            UNION
            SELECT rd.role_id FROM role_department_tb rd
            JOIN user_departments_tb ud ON ud.dept_id = rd.department_id
            WHERE ud.user_id = ?1
            """;

    /**
     * Codes of every enabled permission a user holds. These become the user's
     * Spring authorities (see {@code PermissionChecker}).
     * <ol>
     * <li>Default: the permissions of the user's roles, which for a business user
     * is the role of their department.</li>
     * <li>Page overrides from the "By Permission" screen
     * ({@code page_permission_approvals_record}): a row with
     * {@code permission_enabled = 1} grants the permission even when the
     * department does not; a row with {@code permission_enabled = 0} takes a
     * department default away. A grant on any page wins over a removal.</li>
     * <li>Direct baseline access to the order-audit / evidence menu also grants
     * its exact read capability, never write, approval or wider data-scope
     * permissions. Both menu and grant must be enabled.</li>
     * </ol>
     */
    @NativeQuery("""
            SELECT DISTINCT p.permission_code FROM permission_tb p
            WHERE p.is_enabled = 1 AND (
                (p.id IN (SELECT rp.permission_id FROM role_permission_tb rp
                          WHERE rp.role_id IN (""" + USER_ROLE_IDS + """
                          ))
                 AND NOT EXISTS (SELECT 1 FROM page_permission_approvals_record d
                                 WHERE d.user_id = ?1 AND d.permission_id = p.id AND d.permission_enabled = 0))
                OR p.id IN (SELECT a.permission_id FROM page_permission_approvals_record a
                            WHERE a.user_id = ?1 AND a.permission_enabled = 1)
                OR (p.permission_code IN ('menu.order-audit-task.read','menu.evidence.read') AND EXISTS (
                    SELECT 1 FROM user_nav_menu g
                    JOIN navigation_menu_tb n ON n.id = g.nav_id
                    WHERE g.user_id = ?1 AND g.is_enabled = 1 AND g.permission_id IS NULL
                      AND n.is_enabled = 1 AND ((p.permission_code='menu.order-audit-task.read' AND n.route='/pmc/order-audit-task') OR (p.permission_code='menu.evidence.read' AND n.route='/pmc/evidence')))))
            """)
    List<String> findGrantedCodesByUserId(Long userId);

    /**
     * Ids of the enabled permissions a user gets from their roles (the
     * department default), before any page override is applied.
     */
    @NativeQuery("""
            SELECT DISTINCT rp.permission_id FROM role_permission_tb rp
            JOIN permission_tb p ON p.id = rp.permission_id
            WHERE p.is_enabled = 1 AND rp.role_id IN (""" + USER_ROLE_IDS + """
            )
            """)
    List<Long> findDefaultPermissionIdsByUserId(Long userId);

    /** Codes of the enabled permissions attached to any of the given roles. */
    @NativeQuery("""
            SELECT DISTINCT p.permission_code FROM permission_tb p
            JOIN role_permission_tb rp ON rp.permission_id = p.id
            WHERE p.is_enabled = 1 AND rp.role_id IN (?1)
            """)
    List<String> findEnabledCodesByRoleIds(Collection<Long> roleIds);

    @NativeQuery("SELECT * FROM permission_tb p WHERE p.permission_code = ?1")
    Permission findByPermissionCode(String permissionCode);

    @Modifying
    @Transactional
    @NativeQuery("UPDATE permission_tb p SET p.is_enabled=1 WHERE p.id = ?1")
    void enable(Long id);

}

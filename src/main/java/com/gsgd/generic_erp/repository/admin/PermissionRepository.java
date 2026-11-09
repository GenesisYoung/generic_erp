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
         * Codes of every enabled permission a user holds, either through one of their
         * roles or through a direct per-menu approval. These become the user's
         * Spring authorities (see {@code PermissionChecker}).
         */
        @NativeQuery("""
                        SELECT DISTINCT p.permission_code FROM permission_tb p
                        WHERE p.is_enabled = 1 AND (
                            p.id IN (SELECT rp.permission_id FROM role_permission_tb rp
                                     JOIN user_roles_tb ur ON ur.role_id = rp.role_id
                                     WHERE ur.user_id = ?1)
                            OR p.id IN (SELECT a.permission_id FROM page_permission_approvals_record a
                                        WHERE a.user_id = ?1 AND a.permission_enabled = 1))
                        """)
        List<String> findGrantedCodesByUserId(Long userId);

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

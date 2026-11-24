package com.gsgd.generic_erp.repository.admin;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

import com.gsgd.generic_erp.entity.auth.MenuRegisteredPermissionsRecord;

/** Repository for permission-to-menu registrations. */
public interface MenuRegisteredPermissionRecordRepository extends JpaRepository<MenuRegisteredPermissionsRecord, Long>,
        JpaSpecificationExecutor<MenuRegisteredPermissionsRecord> {

    /** All permissions registered against one menu. */
    @Query("SELECT m FROM MenuRegisteredPermissionsRecord m WHERE m.menuId = :menuId AND m.permissionEnabled = 1")
    List<MenuRegisteredPermissionsRecord> findByMenuId(Long menuId);

    /**
     * The specific registration row for a (menu, permission) pair — the toggle
     * target.
     */
    List<MenuRegisteredPermissionsRecord> findByMenuIdAndPermissionId(Long menuId, Long permissionId);

    /**
     * Menus that have at least one of the given permission codes registered: a
     * user holding such a code (for example from their department role) sees the
     * menu.
     */
    @NativeQuery("""
            SELECT DISTINCT r.menu_id FROM menu_registered_permissions_record r
            JOIN permission_tb p ON p.id = r.permission_id
            WHERE r.permission_enabled = 1 AND p.is_enabled = 1 AND p.permission_code IN (?1)
            """)
    List<Long> findMenuIdsByPermissionCodes(Collection<String> codes);
}

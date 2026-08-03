package com.gsgd.generic_erp.repository.admin;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;

/** Repository for per-user permission approvals on menus. */
public interface PagePermissinApprovalRecordRepository
        extends JpaRepository<PagePermissionApprovalsRecord, Long>,
        JpaSpecificationExecutor<PagePermissionApprovalsRecord> {

    /** Every permission a user has been approved for, across all menus. */
    List<PagePermissionApprovalsRecord> findByUserId(Long userId);

    /** Every approval a user holds for one menu — the cascade-revoke target set. */
    List<PagePermissionApprovalsRecord> findByUserIdAndMenuId(Long userId, Long menuId);

    /** The specific approval row for a (user, menu, permission) triple. */
    List<PagePermissionApprovalsRecord> findByUserIdAndMenuIdAndPermissionId(Long userId, Long menuId,
            Long permissionId);

    /** Which of the given (registered) permissions a user holds for one menu. */
    List<PagePermissionApprovalsRecord> findByUserIdAndMenuIdAndPermissionIdIn(Long userId, Long menuId,
            Collection<Long> permissionIds);

    /** Every user's approval of one permission for one menu — cleared when the permission is de-registered. */
    List<PagePermissionApprovalsRecord> findByMenuIdAndPermissionId(Long menuId, Long permissionId);
}

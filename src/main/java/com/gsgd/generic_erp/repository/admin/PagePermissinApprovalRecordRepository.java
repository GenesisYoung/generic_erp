package com.gsgd.generic_erp.repository.admin;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.NativeQuery;

import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;

public interface PagePermissinApprovalRecordRepository
        extends JpaRepository<PagePermissionApprovalsRecord, Long>,
        JpaSpecificationExecutor<PagePermissinApprovalRecordRepository> {

    @NativeQuery("SELECT * FROM page_permission_approvals_record WHERE user_id=?1 AND menu_id=?2 AND permission_id IN (?3)")
    List<PagePermissionApprovalsRecord> findRegisteredByUserIdAndMenuIdAndPermissionIdIn(Long userId, Long navId,
            Collection<Long> registeredPermissionIds);
}

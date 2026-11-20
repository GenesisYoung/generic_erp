package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gsgd.generic_erp.entity.auth.*;
import com.gsgd.generic_erp.repository.admin.*;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.service.admin.MenuAccessService;

class DisabledPermissionMenuTest {
    final PermissionRepository permissions = mock(PermissionRepository.class);
    final MenuRegisteredPermissionRecordRepository registrations = mock(MenuRegisteredPermissionRecordRepository.class);
    final PagePermissinApprovalRecordRepository approvals = mock(PagePermissinApprovalRecordRepository.class);
    final PermissionChecker checker = mock(PermissionChecker.class);
    final MenuAccessService service = new MenuAccessService(mock(UserNavMenuRepository.class),
            mock(UserRepository.class), permissions, registrations, approvals, checker,
            mock(NavigationMenuRepository.class));

    @Test
    void staleRegistrationNeverShowsDisabledPermissionAndDisabledApprovalIsNotGranted() {
        when(registrations.findByMenuId(18L))
                .thenReturn(List.of(new MenuRegisteredPermissionsRecord(1L, 18L, 113L, (byte) 1, null),
                        new MenuRegisteredPermissionsRecord(2L, 18L, 112L, (byte) 1, null)));
        when(permissions.findAllById(Set.of(113L, 112L)))
                .thenReturn(List.of(new Permission(113L, "menu.evidence.create", 0, null),
                        new Permission(112L, "menu.evidence.read", 1, null)));
        when(approvals.findByUserIdAndMenuIdAndPermissionIdIn(320L, 18L, Set.of(113L, 112L)))
                .thenReturn(List.of(new PagePermissionApprovalsRecord(1L, 320L, 112L, 18L, (byte) 0, null)));
        var result = service.listPermissionsForMenu(18L, 320L);
        assertEquals(1, result.size());
        assertEquals(112L, result.get(0).getId());
        assertFalse(result.get(0).isGranted());
    }

    @Test
    void disabledPermissionCannotBeRegisteredOrGrantedEvenToRoot() {
        when(permissions.findById(113L)).thenReturn(Optional.of(new Permission(113L, "menu.evidence.create", 0, null)));
        when(registrations.findByMenuIdAndPermissionId(18L, 113L))
                .thenReturn(List.of(new MenuRegisteredPermissionsRecord(1L, 18L, 113L, (byte) 1, null)));
        when(checker.has(anyString())).thenReturn(true);
        assertEquals(404, service.setPermissionRegistration(18L, 113L, true).getCode());
        assertEquals(404, service.setPermissionAccess(18L, 320L, 113L, true).getCode());
        verify(registrations, never()).save(any());
        verify(approvals, never()).save(any());
    }

    @Test
    void disabledRegistrationCannotAuthorizeAnEnabledPermission() {
        when(permissions.findById(113L)).thenReturn(Optional.of(new Permission(113L, "menu.evidence.create", 1, null)));
        when(registrations.findByMenuIdAndPermissionId(18L, 113L))
                .thenReturn(List.of(new MenuRegisteredPermissionsRecord(1L, 18L, 113L, (byte) 0, null)));
        assertEquals(404, service.setPermissionAccess(18L, 320L, 113L, true).getCode());
        verify(approvals, never()).save(any());
    }
}

package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.entity.auth.*;
import com.gsgd.generic_erp.repository.admin.*;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.service.admin.MenuAccessService;

/**
 * "By Permission" page overrides on top of the department default: the user
 * holds what their department role gives, unless this page grants (1) or
 * removes (0) a permission for them. Setting a value equal to the default
 * drops the override.
 */
class PageOverrideTest {
    static final long MENU = 18L, USER = 320L, READ = 112L, UPDATE = 113L, EXPORT = 114L;

    final PermissionRepository permissions = mock(PermissionRepository.class);
    final MenuRegisteredPermissionRecordRepository registrations = mock(MenuRegisteredPermissionRecordRepository.class);
    final PagePermissinApprovalRecordRepository approvals = mock(PagePermissinApprovalRecordRepository.class);
    final PermissionChecker checker = mock(PermissionChecker.class);
    final MenuAccessService service = new MenuAccessService(mock(UserNavMenuRepository.class),
            mock(UserRepository.class), permissions, registrations, approvals, checker,
            mock(NavigationMenuRepository.class));

    @BeforeEach
    void setUp() {
        when(registrations.findByMenuId(MENU)).thenReturn(List.of(
                new MenuRegisteredPermissionsRecord(1L, MENU, READ, (byte) 1, null),
                new MenuRegisteredPermissionsRecord(2L, MENU, UPDATE, (byte) 1, null),
                new MenuRegisteredPermissionsRecord(3L, MENU, EXPORT, (byte) 1, null)));
        when(registrations.findByMenuIdAndPermissionId(eq(MENU), anyLong())).thenAnswer(inv -> List.of(
                new MenuRegisteredPermissionsRecord(9L, MENU, inv.getArgument(1), (byte) 1, null)));
        when(permissions.findAllById(Set.of(READ, UPDATE, EXPORT))).thenReturn(List.of(
                new Permission(READ, "menu.x.read", 1, null),
                new Permission(UPDATE, "menu.x.update", 1, null),
                new Permission(EXPORT, "menu.x.export", 1, null)));
        when(permissions.findById(anyLong())).thenAnswer(inv -> Optional.of(
                new Permission(inv.getArgument(0), "menu.x." + inv.getArgument(0), 1, null)));
        // Department role gives read and update.
        when(permissions.findDefaultPermissionIdsByUserId(USER)).thenReturn(List.of(READ, UPDATE));
        when(checker.has(anyString())).thenReturn(true);
    }

    @Test
    void rosterShowsDepartmentDefaultsWithThePageOverridesApplied() {
        when(approvals.findByUserIdAndMenuIdAndPermissionIdIn(USER, MENU, Set.of(READ, UPDATE, EXPORT)))
                .thenReturn(List.of(
                        new PagePermissionApprovalsRecord(1L, USER, UPDATE, MENU, (byte) 0, null),
                        new PagePermissionApprovalsRecord(2L, USER, EXPORT, MENU, (byte) 1, null)));

        Map<Long, PermissionAccessDTO> rows = new HashMap<>();
        service.listPermissionsForMenu(MENU, USER).forEach(r -> rows.put(r.getId(), r));

        assertTrue(rows.get(READ).isGranted(), "default, no override");
        assertTrue(rows.get(READ).isDefaultGranted());
        assertFalse(rows.get(READ).isOverridden());

        assertFalse(rows.get(UPDATE).isGranted(), "default taken away on this page");
        assertTrue(rows.get(UPDATE).isDefaultGranted());
        assertTrue(rows.get(UPDATE).isOverridden());

        assertTrue(rows.get(EXPORT).isGranted(), "granted on this page beyond the default");
        assertFalse(rows.get(EXPORT).isDefaultGranted());
        assertTrue(rows.get(EXPORT).isOverridden());
    }

    @Test
    void removingADefaultStoresAZeroOverride() {
        when(approvals.findByUserIdAndMenuIdAndPermissionId(USER, MENU, UPDATE)).thenReturn(List.of());
        assertEquals(200, service.setPermissionAccess(MENU, USER, UPDATE, false).getCode());
        ArgumentCaptor<PagePermissionApprovalsRecord> saved = ArgumentCaptor.forClass(PagePermissionApprovalsRecord.class);
        verify(approvals).save(saved.capture());
        assertEquals((byte) 0, saved.getValue().getPermissionEnabled());
    }

    @Test
    void grantingBeyondTheDefaultStoresAOneOverride() {
        when(approvals.findByUserIdAndMenuIdAndPermissionId(USER, MENU, EXPORT)).thenReturn(List.of());
        assertEquals(200, service.setPermissionAccess(MENU, USER, EXPORT, true).getCode());
        ArgumentCaptor<PagePermissionApprovalsRecord> saved = ArgumentCaptor.forClass(PagePermissionApprovalsRecord.class);
        verify(approvals).save(saved.capture());
        assertEquals((byte) 1, saved.getValue().getPermissionEnabled());
    }

    @Test
    void settingTheDefaultValueAgainDropsTheOverride() {
        var override = new PagePermissionApprovalsRecord(1L, USER, UPDATE, MENU, (byte) 0, null);
        when(approvals.findByUserIdAndMenuIdAndPermissionId(USER, MENU, UPDATE)).thenReturn(List.of(override));
        assertEquals(200, service.setPermissionAccess(MENU, USER, UPDATE, true).getCode());
        verify(approvals).deleteAll(List.of(override));
        verify(approvals, never()).save(any());
    }

    @Test
    void flippingAnExistingOverrideUpdatesTheSameRow() {
        var override = new PagePermissionApprovalsRecord(1L, USER, EXPORT, MENU, (byte) 1, null);
        when(approvals.findByUserIdAndMenuIdAndPermissionId(USER, MENU, EXPORT)).thenReturn(List.of(override));
        // Export is not a default, so "off" equals the default: the override goes away.
        service.setPermissionAccess(MENU, USER, EXPORT, false);
        verify(approvals).deleteAll(List.of(override));
    }

    @Test
    void cannotGrantWhatTheCallerDoesNotHold() {
        when(checker.has(anyString())).thenReturn(false);
        assertEquals(403, service.setPermissionAccess(MENU, USER, EXPORT, true).getCode());
        verify(approvals, never()).save(any());
    }
}

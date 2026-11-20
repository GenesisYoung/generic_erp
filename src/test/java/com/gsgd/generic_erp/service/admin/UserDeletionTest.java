package com.gsgd.generic_erp.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.configuration.security.RefreshTokenStore;
import com.gsgd.generic_erp.configuration.security.RoleGuard;
import com.gsgd.generic_erp.configuration.security.impl.AuthenticationImpl;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.admin.PagePermissinApprovalRecordRepository;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.admin.UserNavMenuRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserInfoRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;

/** Deleting a user must remove every access binding, and only after the ROOT checks pass. */
class UserDeletionTest {

    private static final Role ROOT = new Role(1L, "ROOT", 1, null);

    private UserRepository users;
    private UserRoleRepository userRoles;
    private UserDepartmentRepository userDepartments;
    private UserNavMenuRepository userNavMenus;
    private PagePermissinApprovalRecordRepository approvals;
    private RefreshTokenStore refreshTokens;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        RoleRepository roles = mock(RoleRepository.class);
        users = mock(UserRepository.class);
        userRoles = mock(UserRoleRepository.class);
        userDepartments = mock(UserDepartmentRepository.class);
        userNavMenus = mock(UserNavMenuRepository.class);
        approvals = mock(PagePermissinApprovalRecordRepository.class);
        refreshTokens = mock(RefreshTokenStore.class);
        when(roles.findByRoleName("ROOT")).thenReturn(Optional.of(ROOT));
        service = new UserManagementService(users, roles, userRoles, users, mock(AuthenticationImpl.class),
                mock(UserInfoRepository.class), userDepartments, new RoleGuard(),
                refreshTokens, new PermissionChecker(), mock(PermissionRepository.class), userNavMenus, approvals);
        authenticateAs("ADMIN");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletingUserRemovesAllAccessBindings() throws Exception {
        User bob = user(5L, "bob");
        when(users.findById(5L)).thenReturn(Optional.of(bob));

        assertEquals(200, service.deleteUser(5L).getCode());

        verify(userRoles).deleteByUserId(5L);
        verify(userDepartments).deleteByUserId(5L);
        verify(userNavMenus).deleteByUserId(5L);
        verify(approvals).deleteByUserId(5L);
        assertEquals((byte) 0, bob.getIsEnabled());
        assertNull(bob.getCurrentSessionId());
        verify(users).save(bob);
        verify(refreshTokens).revoke("bob");
    }

    @Test
    void adminCannotDeleteRootAccountAndNothingIsUnbound() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "other-root")));
        when(userRoles.findByUserId(7L)).thenReturn(List.of(new UserRole(1L, 7L, 1L, (byte) 1, null)));

        assertEquals(403, service.deleteUser(7L).getCode());

        verify(userRoles, never()).deleteByUserId(anyLong());
        verify(userDepartments, never()).deleteByUserId(anyLong());
        verify(userNavMenus, never()).deleteByUserId(anyLong());
        verify(approvals, never()).deleteByUserId(anyLong());
        verify(users, never()).save(any());
    }

    @Test
    void missingUserIsNotFoundAndNothingIsUnbound() throws Exception {
        when(users.findById(9L)).thenReturn(Optional.empty());

        assertEquals(404, service.deleteUser(9L).getCode());

        verify(userRoles, never()).deleteByUserId(anyLong());
        verify(approvals, never()).deleteByUserId(anyLong());
    }

    private static User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setIsEnabled((byte) 1);
        u.setCurrentSessionId("session-" + id);
        return u;
    }

    private static void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}

package com.gsgd.generic_erp.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import com.gsgd.generic_erp.dto.RoleDTO;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.auth.RoleDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RolePermissionRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserInfoRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;
import com.gsgd.generic_erp.util.Language;
import com.gsgd.generic_erp.util.SimpleResponse;

class PrivilegeEscalationTest {

    private static final Role ROOT = new Role(1L, "ROOT", 1, null);
    private static final Role ADMIN = new Role(2L, "ADMIN", 2, null);
    private static final Role CLERK = new Role(100L, "CLERK", 100, null);

    private RoleRepository roles;
    private UserRepository users;
    private UserRoleRepository userRoles;
    private UserManagementService userService;
    private RoleManagementService roleService;
    private PermissionRepository permissions;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        users = mock(UserRepository.class);
        userRoles = mock(UserRoleRepository.class);
        permissions = mock(PermissionRepository.class);
        RoleGuard guard = new RoleGuard();
        PermissionChecker checker = new PermissionChecker();
        when(roles.findByRoleName("ROOT")).thenReturn(Optional.of(ROOT));
        userService = new UserManagementService(users, roles, userRoles, users, mock(AuthenticationImpl.class),
                mock(Language.class), mock(UserInfoRepository.class), mock(UserDepartmentRepository.class), guard,
                mock(RefreshTokenStore.class), checker, permissions);
        roleService = new RoleManagementService(roles, userRoles, permissions,
                mock(RolePermissionRepository.class), mock(RoleDepartmentRepository.class), users, guard, checker);
        authenticateAs("ADMIN", "menu.roleManagement", "menu.userManagement");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCannotGrantRootRole() {
        when(roles.findObjByValue(List.of(1))).thenReturn(List.of(ROOT));
        when(users.findById(5L)).thenReturn(Optional.of(user(5L, "bob")));
        UserDTO dto = new UserDTO();
        dto.setName("bob");
        dto.setRoleList(List.of(1));

        SimpleResponse response = userService.saveOrUpdate(5L, dto);

        assertEquals(403, response.getCode());
        verify(userRoles, never()).saveAll(any());
    }

    @Test
    void adminCannotModifyRootAccount() {
        when(roles.findObjByValue(List.of(100))).thenReturn(List.of(CLERK));
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "other-root")));
        when(userRoles.findByUserId(7L)).thenReturn(List.of(new UserRole(1L, 7L, 1L, (byte) 1, null)));
        UserDTO dto = new UserDTO();
        dto.setName("other-root");
        dto.setRoleList(List.of(100));

        assertEquals(403, userService.saveOrUpdate(7L, dto).getCode());
        verify(users, never()).save(any());
    }

    @Test
    void roleCannotBeRenamedToReservedName() {
        when(roles.findById(100L)).thenReturn(Optional.of(CLERK));

        assertEquals(403, roleService.saveOrUpdate(new RoleDTO(100L, "root", 100)).getCode());
        verify(roles, never()).save(any());
    }

    @Test
    void builtInRoleCannotBeRenamed() {
        when(roles.findById(2L)).thenReturn(Optional.of(ADMIN));

        assertEquals(403, roleService.saveOrUpdate(new RoleDTO(2L, "SUPERVISOR", 2)).getCode());
        verify(roles, never()).save(any());
    }

    private static User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setIsEnabled((byte) 1);
        return u;
    }

    @Test
    void cannotGrantPermissionCallerDoesNotHold() {
        when(roles.findById(100L)).thenReturn(Optional.of(CLERK));
        when(permissions.findById(9L)).thenReturn(Optional.of(new Permission(9L, "product.delete", 1, null)));

        assertEquals(403, roleService.setRolePermission(100L, 9L, true).getCode());
    }

    @Test
    void cannotAssignRoleCarryingPermissionsCallerDoesNotHold() {
        when(roles.findObjByValue(List.of(100))).thenReturn(List.of(CLERK));
        when(users.findById(5L)).thenReturn(Optional.of(user(5L, "bob")));
        when(permissions.findEnabledCodesByRoleIds(List.of(100L))).thenReturn(List.of("product.delete"));
        UserDTO dto = new UserDTO();
        dto.setName("bob");
        dto.setRoleList(List.of(100));

        assertEquals(403, userService.saveOrUpdate(5L, dto).getCode());
        verify(userRoles, never()).saveAll(any());
    }

    private static void authenticateAs(String role, String... permissionCodes) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String code : permissionCodes) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("caller", null, authorities));
    }
}

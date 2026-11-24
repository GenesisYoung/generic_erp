package com.gsgd.generic_erp.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.RoleDepartment;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.auth.RoleDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;

class NotificationRecipientResolverTest {

    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private UserDepartmentRepository userDepartmentRepository;
    private RoleDepartmentRepository roleDepartmentRepository;
    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        userDepartmentRepository = mock(UserDepartmentRepository.class);
        roleDepartmentRepository = mock(RoleDepartmentRepository.class);
        resolver = new NotificationRecipientResolver(roleRepository, userRoleRepository, userDepartmentRepository,
                roleDepartmentRepository);
    }

    @Test
    void collectsUsersOfEveryNamedRoleWithoutDuplicates() {
        when(roleRepository.findByRoleName("pmc")).thenReturn(Optional.of(Role.builder().id(10L).build()));
        when(roleRepository.findByRoleName("purchaser")).thenReturn(Optional.of(Role.builder().id(12L).build()));
        when(userRoleRepository.findByRoleId(10L)).thenReturn(List.of(
                UserRole.builder().userId(1L).roleId(10L).build(),
                UserRole.builder().userId(2L).roleId(10L).build()));
        when(userRoleRepository.findByRoleId(12L)).thenReturn(List.of(
                UserRole.builder().userId(2L).roleId(12L).build(),
                UserRole.builder().userId(3L).roleId(12L).build()));

        assertEquals(Set.of(1L, 2L, 3L), resolver.usersWithRoles(List.of("pmc", "purchaser")));
    }

    @Test
    void includesMembersOfTheDepartmentsLinkedToTheRole() {
        when(roleRepository.findByRoleName("pmc")).thenReturn(Optional.of(Role.builder().id(10L).build()));
        when(userRoleRepository.findByRoleId(10L)).thenReturn(List.of(
                UserRole.builder().userId(1L).roleId(10L).build()));
        when(roleDepartmentRepository.findByRoleId(10L)).thenReturn(List.of(
                RoleDepartment.builder().roleId(10L).departmentId(5L).build()));
        when(userDepartmentRepository.findByDeptId(5L)).thenReturn(List.of(
                UserDepartment.builder().userId(1L).deptId(5L).build(),
                UserDepartment.builder().userId(4L).deptId(5L).build()));

        assertEquals(Set.of(1L, 4L), resolver.usersWithRoles(List.of("pmc")));
    }

    @Test
    void ignoresUnknownRoleNames() {
        when(roleRepository.findByRoleName("nobody")).thenReturn(Optional.empty());
        assertEquals(Set.of(), resolver.usersWithRoles(List.of("nobody")));
    }

    @Test
    void collectsUsersOfDepartmentsAndSkipsNullIds() {
        when(userDepartmentRepository.findByDeptId(5L)).thenReturn(List.of(
                UserDepartment.builder().userId(8L).deptId(5L).build()));
        when(userDepartmentRepository.findByDeptId(6L)).thenReturn(List.of(
                UserDepartment.builder().userId(9L).deptId(6L).build(),
                UserDepartment.builder().userId(8L).deptId(6L).build()));

        assertEquals(Set.of(8L, 9L), resolver.usersInDepartments(Arrays.asList(5L, null, 6L)));
    }
}

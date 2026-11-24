package com.gsgd.generic_erp.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.entity.auth.RoleDepartment;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.repository.admin.DepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RoleDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.service.auth.DepartmentPeers;

/** One role per department, and the shared "yourself or your department" assignment rule. */
class DepartmentRoleTest {

    @Test
    void choosingARoleReplacesTheDepartmentsPreviousRole() {
        RoleDepartmentRepository links = mock(RoleDepartmentRepository.class);
        RoleDepartment old = RoleDepartment.builder().id(1L).roleId(11L).departmentId(5L).build();
        when(links.findByDepartmentId(5L)).thenReturn(List.of(old));
        when(links.existsByRoleIdAndDepartmentId(10L, 5L)).thenReturn(List.of());
        var service = new DepartmentManagementService(mock(DepartmentRepository.class),
                mock(UserDepartmentRepository.class), mock(RoleRepository.class), links);

        assertEquals(200, service.setDepartmentRole(5L, 10L, true).getCode());

        verify(links).delete(old);
        verify(links).save(argThat(l -> l.getRoleId() == 10L && l.getDepartmentId() == 5L));
    }

    @Test
    void peersAreYourselfAndTheMembersOfYourDepartments() {
        UserDepartmentRepository members = mock(UserDepartmentRepository.class);
        when(members.findByUserId(1L)).thenReturn(List.of(
                UserDepartment.builder().userId(1L).deptId(5L).build()));
        when(members.findByDeptId(5L)).thenReturn(List.of(
                UserDepartment.builder().userId(1L).deptId(5L).build(),
                UserDepartment.builder().userId(2L).deptId(5L).build()));
        var peers = new DepartmentPeers(members);

        assertEquals(Set.of(1L, 2L), peers.of(1L));
        assertEquals(Set.of(9L), peers.of(9L), "no department: only yourself");
        assertEquals(true, peers.canAssign(1L, 2L));
        assertEquals(false, peers.canAssign(1L, 3L));
    }
}

package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gsgd.generic_erp.entity.auth.Department;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.repository.admin.DepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;

/** The PMC department is found by name or id, with its sub-departments. */
class ExceptionApproversTest {

    final DepartmentRepository depts = mock(DepartmentRepository.class);
    final UserDepartmentRepository members = mock(UserDepartmentRepository.class);
    final ExceptionApprovers approvers = new ExceptionApprovers(depts, members);

    ExceptionApproversTest() {
        when(depts.findAll()).thenReturn(List.of(
                Department.builder().id(1L).deptName("Head Office").build(),
                Department.builder().id(2L).deptName("pmc 计划部").parentId(1L).build(),
                Department.builder().id(3L).deptName("物控组").parentId(2L).build(),
                Department.builder().id(4L).deptName("采购部").parentId(1L).build(),
                Department.builder().id(5L).deptName("仓库").parentId(1L).build()));
        when(members.findByDeptId(2L)).thenReturn(List.of(member(10L, 2L)));
        when(members.findByDeptId(3L)).thenReturn(List.of(member(11L, 3L)));
        when(members.findByUserId(11L)).thenReturn(List.of(member(11L, 3L)));
        when(members.findByUserId(12L)).thenReturn(List.of(member(12L, 4L)));
    }

    static UserDepartment member(Long user, Long dept) {
        UserDepartment ud = new UserDepartment();
        ud.setUserId(user);
        ud.setDeptId(dept);
        return ud;
    }

    @Test
    void findsPmcByNameIgnoringCaseWithSubDepartments() {
        assertEquals(Set.of(2L, 3L), approvers.departmentIds());
        assertEquals(Set.of(10L, 11L), approvers.users());
        assertTrue(approvers.isApprover(11L));
        assertFalse(approvers.isApprover(12L), "purchasing is not PMC");
        assertFalse(approvers.isApprover(null));
    }

    @Test
    void configuredIdsAddToTheNameMatch() {
        ReflectionTestUtils.setField(approvers, "deptNames", List.of());
        ReflectionTestUtils.setField(approvers, "deptIds", List.of(5L));
        assertEquals(Set.of(5L), approvers.departmentIds());
    }
}

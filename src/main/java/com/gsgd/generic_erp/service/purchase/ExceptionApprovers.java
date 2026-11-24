package com.gsgd.generic_erp.service.purchase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gsgd.generic_erp.entity.auth.Department;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.repository.admin.DepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who approves the resolution of a purchasing exception: the members of the
 * PMC department. The department is found by id
 * ({@code erp.purchase.exception.approver-dept-ids}) and by name
 * ({@code erp.purchase.exception.approver-dept-names}, default {@code PMC}:
 * any department whose name contains it, ignoring case). Sub-departments
 * count too. Approval goes by department, not by role, so it keeps working
 * when each department has a single role.
 */
@Component
@RequiredArgsConstructor
public class ExceptionApprovers {

    private final DepartmentRepository departmentRepository;
    private final UserDepartmentRepository userDepartmentRepository;

    @Value("${erp.purchase.exception.approver-dept-ids:}")
    private List<Long> deptIds = List.of();

    @Value("${erp.purchase.exception.approver-dept-names:PMC}")
    private List<String> deptNames = List.of("PMC");

    /** Ids of the PMC department(s) and their sub-departments. */
    public Set<Long> departmentIds() {
        List<Department> all = departmentRepository.findAll();
        Set<Long> roots = new LinkedHashSet<>();
        deptIds.stream().filter(Objects::nonNull).forEach(roots::add);
        List<String> names = deptNames.stream().filter(Objects::nonNull).map(String::strip)
                .filter(n -> !n.isEmpty()).map(n -> n.toLowerCase(Locale.ROOT)).toList();
        for (Department d : all) {
            String name = d.getDeptName() == null ? "" : d.getDeptName().toLowerCase(Locale.ROOT);
            if (names.stream().anyMatch(name::contains)) {
                roots.add(d.getId());
            }
        }
        Set<Long> out = new LinkedHashSet<>(roots);
        Deque<Long> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Long parent = todo.pop();
            for (Department d : all) {
                if (Objects.equals(d.getParentId(), parent) && out.add(d.getId())) {
                    todo.push(d.getId());
                }
            }
        }
        return out;
    }

    /** User ids of everyone in the PMC department(s). */
    public Set<Long> users() {
        Set<Long> out = new LinkedHashSet<>();
        for (Long dept : departmentIds()) {
            userDepartmentRepository.findByDeptId(dept).stream().map(UserDepartment::getUserId).forEach(out::add);
        }
        return out;
    }

    /** Whether the user belongs to the PMC department (or one under it). */
    public boolean isApprover(Long userId) {
        if (userId == null) {
            return false;
        }
        List<UserDepartment> mine = userDepartmentRepository.findByUserId(userId);
        if (mine.isEmpty()) {
            return false;
        }
        Set<Long> pmc = departmentIds();
        return mine.stream().anyMatch(ud -> pmc.contains(ud.getDeptId()));
    }
}

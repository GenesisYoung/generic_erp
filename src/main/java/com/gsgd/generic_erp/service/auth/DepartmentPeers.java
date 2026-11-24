package com.gsgd.generic_erp.service.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * The assignment rule shared by every task type: a task can be given to
 * yourself or to another member of a department you belong to. There is no
 * manager / clerk split any more; each department has one role.
 */
@Service
@RequiredArgsConstructor
public class DepartmentPeers {

    private final UserDepartmentRepository userDepartmentRepository;

    /**
     * Ids of the users a task can be assigned to by {@code userId}: the user
     * themselves plus every member of each of their departments. Disabled
     * accounts are filtered by the caller.
     */
    public Set<Long> of(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (userId == null) {
            return ids;
        }
        ids.add(userId);
        List<Long> deptIds = userDepartmentRepository.findByUserId(userId).stream()
                .map(UserDepartment::getDeptId)
                .distinct()
                .toList();
        for (Long deptId : deptIds) {
            userDepartmentRepository.findByDeptId(deptId).stream()
                    .map(UserDepartment::getUserId)
                    .forEach(ids::add);
        }
        return ids;
    }

    /** True when {@code assigneeId} is {@code userId} or shares a department with them. */
    public boolean canAssign(Long userId, Long assigneeId) {
        return assigneeId != null && of(userId).contains(assigneeId);
    }
}

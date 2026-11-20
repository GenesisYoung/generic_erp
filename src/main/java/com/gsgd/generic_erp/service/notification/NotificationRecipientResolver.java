package com.gsgd.generic_erp.service.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns "everyone with role X" or "everyone in department Y" into user ids, so
 * business code can say who should be notified without knowing how users are
 * linked to roles and departments.
 * <p>
 * Role bindings that are disabled ({@code user_enabled = 0}) are skipped by
 * {@link UserRoleRepository#findByRoleId}. Disabled accounts are filtered again
 * when the notification is sent.
 */
@Service
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserDepartmentRepository userDepartmentRepository;

    /** Ids of users holding any of the given role names. Unknown names are ignored. */
    public Set<Long> usersWithRoles(Collection<String> roleNames) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : roleNames) {
            roleRepository.findByRoleName(name).ifPresent(role -> userRoleRepository.findByRoleId(role.getId())
                    .stream()
                    .map(UserRole::getUserId)
                    .forEach(ids::add));
        }
        return ids;
    }

    /** Ids of users linked to any of the given departments. */
    public Set<Long> usersInDepartments(Collection<Long> deptIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            userDepartmentRepository.findByDeptId(deptId).stream()
                    .map(UserDepartment::getUserId)
                    .forEach(ids::add);
        }
        return ids;
    }
}

package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.dto.RoleDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.RolePermission;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.auth.RoleDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RolePermissionRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

/**
 * Backs the "Default permission allocation" screen (role &harr; permission).
 *
 * <ul>
 * <li>Role list: paginated CRUD. A role cannot be deleted while any user is
 * still assigned to it ({@code user_roles_tb}); deleting an unused role first
 * clears its permission and department links so no orphaned join rows remain.</li>
 * <li>Associated permissions: for a selected role, every permission is listed
 * (paginated) flagged with whether a {@code role_permission_tb} link already
 * exists ("Processed" vs "Not granted"), and that link can be toggled.</li>
 * </ul>
 */
@Service
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleDepartmentRepository roleDepartmentRepository;
    private final UserRepository userRepository;

    public RoleManagementService(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
            PermissionRepository permissionRepository, RolePermissionRepository rolePermissionRepository,
            RoleDepartmentRepository roleDepartmentRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleDepartmentRepository = roleDepartmentRepository;
        this.userRepository = userRepository;
    }

    /** Returns a page of roles mapped to {@link RoleDTO}. */
    public BasicPageResponse<Role, RoleDTO> fetchRoles(Pageable pageable) {
        Page<Role> page = roleRepository.findAll(pageable);
        List<RoleDTO> dto = page.getContent().stream()
                .map(r -> new RoleDTO(r.getId(), r.getRoleName(), r.getVal()))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /** Creates a role (id == null) or renames/updates an existing one. */
    public SimpleResponse saveOrUpdate(RoleDTO dto) {
        if (dto.getId() == null) {
            if (roleRepository.existsByRoleName(dto.getRoleName())) {
                return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
            }
            roleRepository.save(Role.builder()
                    .roleName(dto.getRoleName())
                    .val(dto.getVal())
                    .createDate(LocalDate.now())
                    .build());
            return new SimpleResponse(200, "");
        }
        Role role = roleRepository.findById(dto.getId()).orElseThrow();
        role.setRoleName(dto.getRoleName());
        role.setVal(dto.getVal());
        roleRepository.save(role);
        return new SimpleResponse(200, "");
    }

    /**
     * Deletes a role, but only when no user is still assigned to it. The role's
     * permission and department links are removed first to satisfy the foreign
     * keys in {@code role_permission_tb} / {@code role_department_tb}.
     */
    @Transactional
    public SimpleResponse deleteRole(Long roleId) throws NoneDeleteableException {
        if (!userRoleRepository.findByRoleId(roleId).isEmpty()) {
            throw new NoneDeleteableException(Language_CN.ROLE_ASSIGNED.getMessage());
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        roleDepartmentRepository.deleteByRoleId(roleId);
        roleRepository.deleteById(roleId);
        return new SimpleResponse(200, "");
    }

    /**
     * Every permission (paginated), each flagged with whether the given role
     * currently holds it via {@code role_permission_tb}.
     */
    public BasicPageResponse<Permission, PermissionAccessDTO> listPermissionsForRole(Long roleId, Pageable pageable) {
        Set<Long> grantedIds = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toSet());
        Page<Permission> page = permissionRepository.findAll(pageable);
        List<PermissionAccessDTO> dto = page.getContent().stream()
                .map(p -> new PermissionAccessDTO(p.getId(), p.getPermissionCode(), grantedIds.contains(p.getId())))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /** Grants or revokes a single permission for a role (idempotent). */
    @Transactional
    public SimpleResponse setRolePermission(Long roleId, Long permissionId, boolean granted) {
        boolean exists = rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId);
        if (granted) {
            if (!exists) {
                rolePermissionRepository.save(RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .createdBy(currentUserId())
                        .createTime(LocalDateTime.now())
                        .createDate(LocalDate.now())
                        .build());
            }
        } else if (exists) {
            rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
        }
        return new SimpleResponse(200, "");
    }

    /**
     * Resolves the id of the currently authenticated user for the {@code created_by}
     * audit column. Falls back to the root user (id 1) when no username-based
     * principal is present (e.g. system calls).
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
            return userRepository.findByUsername(details.getUsername())
                    .map(u -> u.getId())
                    .orElse(1L);
        }
        return 1L;
    }
}

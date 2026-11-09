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
import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.configuration.security.RoleGuard;
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
import com.gsgd.generic_erp.spec.PermissionSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

/**
 * Backs the "Default permission allocation" screen (role &harr; permission).
 *
 * <ul>
 * <li>Role list: paginated CRUD. A role cannot be deleted while any user is
 * still assigned to it ({@code user_roles_tb}); deleting an unused role first
 * clears its permission and department links so no orphaned join rows
 * remain.</li>
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
    private final RoleGuard roleGuard;
    private final PermissionChecker permissionChecker;

    public RoleManagementService(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
            PermissionRepository permissionRepository, RolePermissionRepository rolePermissionRepository,
            RoleDepartmentRepository roleDepartmentRepository, UserRepository userRepository,
            RoleGuard roleGuard, PermissionChecker permissionChecker) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleDepartmentRepository = roleDepartmentRepository;
        this.userRepository = userRepository;
        this.roleGuard = roleGuard;
        this.permissionChecker = permissionChecker;
    }

    /** Returns a page of roles mapped to {@link RoleDTO}. */
    public BasicPageResponse<Role, RoleDTO> fetchRoles(Pageable pageable) {
        Page<Role> page = roleRepository.findAll(pageable);
        List<RoleDTO> dto = page.getContent().stream()
                .map(r -> new RoleDTO(r.getId(), r.getRoleName(), r.getVal()))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /**
     * Creates a role (id == null) or renames/updates an existing one.
     * <p>
     * Authorities are derived from the role name, so the built-in ROOT/ADMIN
     * roles are immutable and their names (case-insensitively) are reserved;
     * otherwise renaming any role to "ROOT" would grant ROOT to its holders.
     * {@code val} is the external code used to assign roles, so it must be
     * unique too.
     */
    public SimpleResponse saveOrUpdate(RoleDTO dto) {
        String name = dto.getRoleName() == null ? null : dto.getRoleName().trim();
        if (name == null || name.isEmpty() || dto.getVal() == null) {
            return new SimpleResponse(400, "Role name and value are required");
        }
        if (dto.getId() == null) {
            if (roleGuard.isBuiltInRoleName(name)) {
                return new SimpleResponse(403, "Reserved role name");
            }
            if (roleRepository.existsByRoleName(name)) {
                return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
            }
            if (roleRepository.existsByVal(dto.getVal())) {
                return new SimpleResponse(201, "Role value already in use");
            }
            roleRepository.save(Role.builder()
                    .roleName(name)
                    .val(dto.getVal())
                    .createDate(LocalDate.now())
                    .build());
            return new SimpleResponse(200, "");
        }
        Role role = roleRepository.findById(dto.getId()).orElse(null);
        if (role == null) {
            return new SimpleResponse(404, "Role not found");
        }
        if (roleGuard.isBuiltInRoleName(role.getRoleName()) || roleGuard.isBuiltInRoleName(name)) {
            return new SimpleResponse(403, "Built-in roles cannot be modified");
        }
        if (roleRepository.existsByRoleNameIgnoreCaseAndIdNot(name, role.getId())) {
            return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
        }
        if (roleRepository.existsByValAndIdNot(dto.getVal(), role.getId())) {
            return new SimpleResponse(201, "Role value already in use");
        }
        role.setRoleName(name);
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
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return new SimpleResponse(404, "Role not found");
        }
        if (roleGuard.isBuiltInRoleName(role.getRoleName())) {
            throw new NoneDeleteableException("Built-in roles cannot be deleted");
        }
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
        Page<Permission> page = permissionRepository.findAll(PermissionSpecification.enabled(), pageable);
        List<PermissionAccessDTO> dto = page.getContent().stream()
                .map(p -> new PermissionAccessDTO(p.getId(), p.getPermissionCode(), grantedIds.contains(p.getId())))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /** Grants or revokes a single permission for a role (idempotent). */
    @Transactional
    public SimpleResponse setRolePermission(Long roleId, Long permissionId, boolean granted) {
        Role role = roleRepository.findById(roleId).orElse(null);
        Permission permission = permissionRepository.findById(permissionId).orElse(null);
        if (role == null || permission == null) {
            return new SimpleResponse(404, "Role or permission not found");
        }
        if (granted && !permissionChecker.has(permission.getPermissionCode())) {
            return new SimpleResponse(403, "You cannot grant a permission you do not hold");
        }
        if (RoleGuard.ROOT_ROLE.equals(role.getRoleName()) && !roleGuard.currentUserIsRoot()) {
            return new SimpleResponse(403, "Only ROOT can change ROOT permissions");
        }
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
     * Resolves the id of the currently authenticated user for the
     * {@code created_by}
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

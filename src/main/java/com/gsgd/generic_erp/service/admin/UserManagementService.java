package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.configuration.security.RefreshTokenStore;
import com.gsgd.generic_erp.configuration.security.RoleGuard;
import com.gsgd.generic_erp.configuration.security.impl.AuthenticationImpl;
import com.gsgd.generic_erp.controller.exception.GlobalExceptionHandler;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.entity.auth.UserInfo;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.enums.Language_EN;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserInfoRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;
import com.gsgd.generic_erp.spec.UserSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.Language;
import com.gsgd.generic_erp.util.SimpleResponse;
import com.gsgd.generic_erp.view.UserRoleView;

/**
 * Administrative user management: paginated listing (disabled users excluded),
 * create/update with role diffing, and soft deletion.
 */
@Service
public class UserManagementService {
    // private final JWTUtil JWTUtil;
    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final AuthenticationImpl authenticationService;
    private final UserInfoRepository infoRepository;
    private final UserDepartmentRepository uDepartmentRepositoryrepository;
    private final Language language;
    private final RoleGuard roleGuard;
    private final RefreshTokenStore refreshTokenStore;
    private final PermissionChecker permissionChecker;
    private final PermissionRepository permissionRepository;
    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public UserManagementService(UserRepository repository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository,
            AuthenticationImpl aService, Language language, UserInfoRepository infoRepository,
            UserDepartmentRepository userDepartmentRepository, RoleGuard roleGuard,
            RefreshTokenStore refreshTokenStore, PermissionChecker permissionChecker,
            PermissionRepository permissionRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        // this.JWTUtil = JWTUtil;
        this.authenticationService = aService;
        this.language = language;
        this.infoRepository = infoRepository;
        this.uDepartmentRepositoryrepository = userDepartmentRepository;
        this.roleGuard = roleGuard;
        this.refreshTokenStore = refreshTokenStore;
        this.permissionChecker = permissionChecker;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Returns a page of enabled users, each mapped to a DTO with its roles
     * attached.
     */
    @Cacheable(value = "userQuery", keyGenerator = "jsonKeyGenerator")
    public BasicPageResponse<User, UserDTO> fetchUserList(Pageable pageable) {
        Page<User> users = repository.findAll(UserSpecification.excludeDisabled((byte) 1), pageable);

        @SuppressWarnings("null")
        // 1. Collect this page's user IDs
        List<Long> userIds = users.getContent().stream()
                .map(User::getId)
                .toList();

        @SuppressWarnings("null")
        // 2. One query for every role link on the page
        Map<Long, List<UserRoleView>> rolesByUser = userIds.isEmpty()
                ? Map.of()
                : userRoleRepository.findRoleViewsByUserIds(userIds).stream()
                        .collect(Collectors.groupingBy(UserRoleView::getUserId));
        // 3. Build DTOs with O(1) map lookups
        List<UserDTO> dto = users.getContent().stream()
                .map(u -> {
                    Optional<UserInfo> op = infoRepository.findByUserId(u.getId());
                    UserInfo info = null;
                    if (op.isPresent()) {
                        info = op.get();
                    }
                    List<Long> departments = uDepartmentRepositoryrepository.findByUserId(u.getId()).stream()
                            .map(ele -> ele.getDeptId()).toList();
                    return new UserDTO(
                            u.getId(),
                            u.getUsername(),
                            u.getEmail(),
                            u.getDisplayName(),
                            rolesByUser.getOrDefault(u.getId(), new ArrayList<>()),
                            u.getStatus(),
                            u.getIsEnabled(), null, info != null ? info.getRealName() : null,
                            info != null ? info.getTitle() : null,
                            info != null ? info.getBirthday() : null,
                            info != null ? info.getHireDate() : null,
                            departments,
                            null);
                })
                .toList();

        return new BasicPageResponse<>(dto, users);
    }

    /**
     * Creates a user ({@code userId} null or 0) or updates an existing one.
     * <p>
     * On update, the user's role and department links are reconciled with a set
     * diff: links no longer selected are bulk-deleted and newly selected ones
     * inserted. A {@code null} role or department list leaves that side
     * unchanged; an empty list clears it.
     * <p>
     * Only a ROOT caller may assign the ROOT role or modify an account that
     * already holds it, and the seeded root account can never be disabled or
     * lose ROOT. New users must be provisioned with a strong initial password.
     */
    @Transactional
    @CacheEvict(cacheNames = "userQuery", allEntries = true)
    public SimpleResponse saveOrUpdate(Long userId, UserDTO user) {
        boolean creating = userId == null || userId == 0;
        boolean callerIsRoot = roleGuard.currentUserIsRoot();

        // Resolve requested role codes up front so unknown codes are rejected.
        List<Role> requestedRoles = null;
        if (user.getRoleList() != null) {
            List<Integer> codes = user.getRoleList().stream().filter(Objects::nonNull).distinct().toList();
            requestedRoles = codes.isEmpty() ? List.of() : roleRepository.findObjByValue(codes);
            if (requestedRoles.size() != codes.size()) {
                return new SimpleResponse(400, "Unknown role");
            }
        }
        boolean requestsRoot = requestedRoles != null && requestedRoles.stream()
                .anyMatch(r -> RoleGuard.ROOT_ROLE.equals(r.getRoleName()));
        if (requestsRoot && !callerIsRoot) {
            return new SimpleResponse(403, "Only ROOT can assign the ROOT role");
        }

        if (creating) {
            if (user.getInitialPassword() == null || user.getInitialPassword().length() < 12) {
                throw new IllegalArgumentException("Initial password must be at least 12 characters");
            }
            if (userRepository.existsByUsername(user.getName())) {
                throw new DataIntegrityViolationException(message(Language_EN.USERNAME_DULICATED.getMessage(),
                        Language_CN.USERNAME_DULICATED.getMessage()));
            }
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new DataIntegrityViolationException(message(Language_EN.EMAIL_DULICATED.getMessage(),
                        Language_CN.EMAIL_DULICATED.getMessage()));
            }
            if (requestedRoles != null && !canAssign(requestedRoles.stream().map(Role::getId).toList())) {
                return new SimpleResponse(403, "You cannot assign a role with permissions you do not hold");
            }
            User saved = repository.save(injectUserDTO(user, null));
            UserInfo info = new UserInfo();
            info.setUserId(saved.getId());
            applyInfo(info, user);
            infoRepository.save(info);
            if (user.getDepartments() != null) {
                new HashSet<>(user.getDepartments()).forEach(deptId -> uDepartmentRepositoryrepository.save(
                        UserDepartment.builder().deptId(deptId).userId(saved.getId()).build()));
            }
            if (requestedRoles != null && !requestedRoles.isEmpty()) {
                userRoleRepository.saveAll(requestedRoles.stream()
                        .map(r -> newUserRole(saved.getId(), r.getId()))
                        .toList());
            }
            return new SimpleResponse(200, "Successfully created");
        }

        User existing = userRepository.findById(userId).orElse(null);
        if (existing == null) {
            return new SimpleResponse(404, "User not found");
        }
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        Set<Long> currentRoleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        Long rootRoleId = roleRepository.findByRoleName(RoleGuard.ROOT_ROLE).map(Role::getId).orElse(null);
        boolean targetIsRoot = userId == RoleGuard.ROOT_USER_ID
                || (rootRoleId != null && currentRoleIds.contains(rootRoleId));
        if (targetIsRoot && !callerIsRoot) {
            return new SimpleResponse(403, "Only ROOT can modify a ROOT account");
        }
        if (userId == RoleGuard.ROOT_USER_ID) {
            if ((requestedRoles != null && !requestsRoot)
                    || (user.getIsEnabled() != null && user.getIsEnabled() != 1)
                    || Boolean.FALSE.equals(user.getActive())) {
                return new SimpleResponse(403, "The root account cannot be disabled or lose ROOT");
            }
        }
        if (user.getName() != null && !user.getName().equals(existing.getUsername())
                && userRepository.existsByUsername(user.getName())) {
            throw new DataIntegrityViolationException(message(Language_EN.USERNAME_DULICATED.getMessage(),
                    Language_CN.USERNAME_DULICATED.getMessage()));
        }
        if (user.getEmail() != null && !user.getEmail().equals(existing.getEmail())
                && userRepository.existsByEmail(user.getEmail())) {
            throw new DataIntegrityViolationException(message(Language_EN.EMAIL_DULICATED.getMessage(),
                    Language_CN.EMAIL_DULICATED.getMessage()));
        }

        if (requestedRoles != null) {
            List<Long> added = requestedRoles.stream().map(Role::getId)
                    .filter(id -> !currentRoleIds.contains(id)).toList();
            if (!canAssign(added)) {
                return new SimpleResponse(403, "You cannot assign a role with permissions you do not hold");
            }
        }

        UserInfo info = infoRepository.findByUserId(userId).orElseGet(UserInfo::new);
        info.setUserId(userId);
        applyInfo(info, user);
        infoRepository.save(info);

        if (user.getDepartments() != null) {
            Set<Long> existedDepts = uDepartmentRepositoryrepository.findByUserId(userId).stream()
                    .map(UserDepartment::getDeptId)
                    .collect(Collectors.toSet());
            Set<Long> latestDepts = new HashSet<>(user.getDepartments());
            Set<Long> deptsToRemove = new HashSet<>(existedDepts);
            deptsToRemove.removeAll(latestDepts);
            Set<Long> deptsToAdd = new HashSet<>(latestDepts);
            deptsToAdd.removeAll(existedDepts);
            if (!deptsToRemove.isEmpty()) {
                uDepartmentRepositoryrepository.deleteThroughUserIdAndDeptIds(userId, deptsToRemove);
            }
            deptsToAdd.forEach(deptId -> uDepartmentRepositoryrepository.save(
                    UserDepartment.builder().deptId(deptId).userId(userId).build()));
        }

        if (requestedRoles != null) {
            Set<Long> latestIds = requestedRoles.stream().map(Role::getId).collect(Collectors.toSet());
            Set<Long> rolesToRemove = new HashSet<>(currentRoleIds);
            rolesToRemove.removeAll(latestIds);
            Set<Long> rolesToAdd = new HashSet<>(latestIds);
            rolesToAdd.removeAll(currentRoleIds);
            if (!rolesToRemove.isEmpty()) {
                userRoleRepository.deleteByUserIdAndRoleIdIn(userId, rolesToRemove);
            }
            if (!rolesToAdd.isEmpty()) {
                userRoleRepository.saveAll(rolesToAdd.stream().map(roleId -> newUserRole(userId, roleId)).toList());
            }
        }

        String previousUsername = existing.getUsername();
        User updated = injectUserDTO(user, existing);
        boolean deactivated = updated.getIsEnabled() == null || updated.getIsEnabled() != 1
                || Boolean.FALSE.equals(updated.getStatus());
        boolean renamed = !previousUsername.equals(updated.getUsername());
        if (deactivated || renamed) {
            // Kill the live session immediately instead of waiting for token expiry.
            updated.setCurrentSessionId(null);
            refreshTokenStore.revoke(previousUsername);
        }
        repository.save(updated);

        return new SimpleResponse(200, "Successfully updated");
    }

    /**
     * Soft-deletes a user by setting {@code isEnabled = 0}; the row is kept for
     * audit history. The root user (id 1) cannot be deleted, and only ROOT may
     * delete another ROOT account. The user's session is revoked immediately.
     *
     * @throws NoneDeleteableException
     */
    @Transactional
    @CacheEvict(cacheNames = "userQuery", allEntries = true)
    public SimpleResponse deleteUser(Long id) throws NoneDeleteableException {
        if (id == null) {
            return new SimpleResponse(400, "User id is required");
        }
        if (id == RoleGuard.ROOT_USER_ID) {
            throw new NoneDeleteableException(message(Language_EN.CANT_DELETE_ROOT_USER.getMessage(),
                    Language_CN.CANT_DELETE_ROOT_USER.getMessage()));
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return new SimpleResponse(404, "User not found");
        }
        Long rootRoleId = roleRepository.findByRoleName(RoleGuard.ROOT_ROLE).map(Role::getId).orElse(null);
        boolean targetIsRoot = rootRoleId != null && userRoleRepository.findByUserId(id).stream()
                .anyMatch(ur -> rootRoleId.equals(ur.getRoleId()));
        if (targetIsRoot && !roleGuard.currentUserIsRoot()) {
            return new SimpleResponse(403, "Only ROOT can delete a ROOT account");
        }
        target.setIsEnabled((byte) 0);
        target.setCurrentSessionId(null);
        userRepository.save(target);
        refreshTokenStore.revoke(target.getUsername());
        return new SimpleResponse(200, "successfully deleted");
    }

    /**
     * Maps a {@link UserDTO} onto a {@link User} entity: mutates {@code existing}
     * when present, otherwise builds a fresh enabled user with the hashed
     * initial password.
     */
    private User injectUserDTO(UserDTO user, User existing) {
        if (existing != null) {
            existing.setUsername(user.getName());
            existing.setDisplayName(user.getDisplayName());
            existing.setEmail(user.getEmail());
            existing.setStatus(user.getActive());
            if (user.getIsEnabled() != null) {
                existing.setIsEnabled(user.getIsEnabled());
            }
            return existing;
        }
        User u = new User();
        u.setUsername(user.getName());
        u.setDisplayName(user.getDisplayName());
        u.setEmail(user.getEmail());
        u.setStatus(user.getActive());
        u.setPassword(authenticationService.generatePass(user.getInitialPassword()));
        u.setIsEnabled((byte) 1);
        return u;
    }

    private void applyInfo(UserInfo info, UserDTO user) {
        info.setRealName(user.getRealName());
        info.setTitle(user.getTitle());
        if (info.getCreateDate() == null) {
            info.setCreateDate(LocalDate.now());
        }
        if (user.getBirthday() != null)
            info.setBirthday(user.getBirthday());
        if (user.getHireDate() != null)
            info.setHireDate(user.getHireDate());
    }

    /**
     * A caller may only hand out roles whose permissions they hold themselves,
     * otherwise anyone with user-management rights could escalate via roles.
     */
    private boolean canAssign(List<Long> roleIds) {
        if (roleIds.isEmpty() || roleGuard.currentUserIsRoot()) {
            return true;
        }
        return permissionChecker.hasAll(permissionRepository.findEnabledCodesByRoleIds(roleIds));
    }

    private UserRole newUserRole(Long userId, Long roleId) {
        return new UserRole(null, userId, roleId, (byte) 1, LocalDate.now());
    }

    /** Localized message; falls back to English for any non-CN setting. */
    private String message(String en, String cn) {
        return "CN".equals(language.getLanguage()) ? cn : en;
    }

}

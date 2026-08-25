package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.configuration.security.impl.AuthenticationImpl;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserDepartment;
import com.gsgd.generic_erp.entity.auth.UserInfo;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.enums.Language_EN;
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

    public UserManagementService(UserRepository repository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository,
            AuthenticationImpl aService, Language language, UserInfoRepository infoRepository,
            UserDepartmentRepository userDepartmentRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        // this.JWTUtil = JWTUtil;
        this.authenticationService = aService;
        this.language = language;
        this.infoRepository = infoRepository;
        this.uDepartmentRepositoryrepository = userDepartmentRepository;
    }

    /**
     * Returns a page of enabled users, each mapped to a DTO with its roles
     * attached.
     */
    @Cacheable(value = "userQuery", key = "#pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
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
     * Creates a user ({@code userId == 0}) or updates an existing one.
     * <p>
     * On update, the user's role links are reconciled with a set diff:
     * roles no longer selected are bulk-deleted and newly selected roles are
     * bulk-inserted, keeping the whole operation to a handful of queries.
     * New users must be provisioned with an explicit strong initial password.
     */
    @Transactional
    @CacheEvict(cacheNames = "userQuery", allEntries = true)
    public SimpleResponse saveOrUpdate(Long userId, UserDTO user) {
        try {
            // Check for duplicate username/email on create
            if (userId == 0) {
                if (user.getInitialPassword() == null || user.getInitialPassword().length() < 12) {
                    throw new IllegalArgumentException("Initial password must be at least 12 characters");
                }
                if (userRepository.existsByUsername(user.getName())) {
                    if (language.getLanguage().equals("EN")) {
                        throw new DataIntegrityViolationException(Language_EN.USERNAME_DULICATED.getMessage());
                    } else if (language.getLanguage().equals("CN")) {
                        throw new DataIntegrityViolationException(Language_CN.USERNAME_DULICATED.getMessage());
                    }
                }
                if (userRepository.existsByEmail(user.getEmail())) {
                    if (language.getLanguage().equals("EN")) {
                        throw new DataIntegrityViolationException(Language_EN.EMAIL_DULICATED.getMessage());
                    } else if (language.getLanguage().equals("CN")) {
                        throw new DataIntegrityViolationException(Language_CN.EMAIL_DULICATED.getMessage());
                    }
                }
                User saved = repository.save(injectUserDTO(user, userId));
                /**
                 * Upate related infomation of current user
                 */
                UserInfo info = new UserInfo();
                info.setUserId(user.getId());
                info.setRealName(user.getRealName());
                info.setTitle(user.getTitle());
                info.setCreateDate(LocalDate.now());
                if (user.getBirthday() != null)
                    info.setBirthday(user.getBirthday());
                if (user.getHireDate() != null)
                    info.setHireDate(user.getHireDate());
                infoRepository.save(info);
                /**
                 * Build constraints between users and departments
                 */
                if (user.getDepartments() != null)
                    user.getDepartments().stream().forEach(ele -> {
                        uDepartmentRepositoryrepository.save(
                                UserDepartment.builder()
                                        .deptId(ele)
                                        .userId(saved.getId())
                                        .build());
                    });
                /**
                 * Assign roles for current user
                 */
                for (Integer role : user.getRoleList()) {
                    Long id = roleRepository.getIdByVal(role);
                    userRoleRepository.save(new UserRole(null, saved.getId(), id, LocalDate.now()));
                }
                return new SimpleResponse(200, "Successfully created");
            } else {
                /**
                 * Update information of this user
                 */
                Optional<UserInfo> i = infoRepository.findByUserId(userId);
                if (!i.isEmpty()) {
                    UserInfo info = i.get();
                    info.setUserId(userId);
                    info.setRealName(user.getRealName());
                    info.setTitle(user.getTitle());
                    info.setCreateDate(LocalDate.now());
                    if (user.getBirthday() != null)
                        info.setBirthday(
                                user.getBirthday());
                    if (user.getHireDate() != null)
                        info.setHireDate(
                                user.getHireDate());
                    infoRepository.save(info);
                } else {
                    UserInfo info = new UserInfo();
                    info.setRealName(user.getRealName());
                    info.setTitle(user.getTitle());
                    info.setCreateDate(LocalDate.now());
                    info.setUserId(userId);
                    if (user.getBirthday() != null)
                        info.setBirthday(
                                user.getBirthday());
                    if (user.getHireDate() != null)
                        info.setHireDate(
                                user.getHireDate());
                    infoRepository.save(info);
                }
                Set<Long> toRemove = null;
                Set<Long> toAdd = null;
                /**
                 * Update department infomation of the user
                 */
                List<UserDepartment> ud = uDepartmentRepositoryrepository.findByUserId(userId);
                @SuppressWarnings("null")
                Set<Long> existedDepts = ud
                        .stream()
                        .map(UserDepartment::getDeptId)
                        .collect(Collectors.toSet());
                toRemove = new HashSet<>(existedDepts);
                toRemove.removeAll(user.getDepartments());
                toAdd = new HashSet<>(user.getDepartments());
                toAdd.removeAll(existedDepts);
                if (!toRemove.isEmpty()) {
                    uDepartmentRepositoryrepository.deleteThroughUserIdAndDeptIds(userId, toRemove);
                }
                if (!toAdd.isEmpty()) {
                    toAdd.stream().forEach(ele -> {
                        uDepartmentRepositoryrepository.save(
                                UserDepartment.builder()
                                        .deptId(ele)
                                        .userId(userId)
                                        .build());
                    });
                }

                List<Role> roles = roleRepository.findObjByValue(user.getRoleList());
                List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
                if (roles != null && userRoles != null && !roles.isEmpty() && !userRoles.isEmpty()) {
                    // 1. Resolve the latest roles to a Set of IDs (one query)
                    @SuppressWarnings("null")
                    Set<Long> latestIds = roles
                            .stream()
                            .map(Role::getId)
                            .collect(Collectors.toSet());

                    // 2. Resolve the current role IDs (one query)
                    @SuppressWarnings("null")
                    Set<Long> currentIds = userRoles
                            .stream()
                            .map(UserRole::getRoleId)
                            .collect(Collectors.toSet());

                    // 3. Compute the diff
                    // toRemove = current - latest
                    toRemove = new HashSet<>(currentIds);
                    toRemove.removeAll(latestIds);

                    // toAdd = latest - current
                    toAdd = new HashSet<>(latestIds);
                    toAdd.removeAll(currentIds);

                    // 4. Bulk delete (one query)
                    if (!toRemove.isEmpty()) {
                        userRoleRepository.deleteByUserIdAndRoleIdIn(userId, toRemove);
                    }

                    // 5. Bulk insert (one query)
                    if (!toAdd.isEmpty()) {
                        List<UserRole> newLinks = toAdd.stream()
                                .map(roleId -> {
                                    UserRole ur = new UserRole();
                                    ur.setUserId(userId);
                                    ur.setRoleId(roleId);
                                    return ur;
                                })
                                .toList();
                        userRoleRepository.saveAll(newLinks);
                    }
                }

                repository.save(injectUserDTO(user, userId));

                return new SimpleResponse(200, "Successfully created");
            }
        } catch (NullPointerException exception) {
            System.err.println(exception.getLocalizedMessage());
            throw new NullPointerException(exception.getLocalizedMessage());
        }
    }

    /**
     * Soft-deletes a user by setting {@code isEnabled = 0}; the row is kept for
     * audit history. The root user (id 1) cannot be deleted.
     * 
     * @throws NoneDeleteableException
     */
    @Transactional
    @CacheEvict(cacheNames = "userQuery", allEntries = true)
    public SimpleResponse deleteUser(Long id) throws NoneDeleteableException {
        if (id == 1L) {
            if (language.getLanguage().equals("EN")) {
                throw new NoneDeleteableException(Language_EN.CANT_DELETE_ROOT_USER.getMessage());
            } else {
                throw new NoneDeleteableException(Language_CN.CANT_DELETE_ROOT_USER.getMessage());
            }
        }
        userRepository.updateIsEnabled(id, (byte) 0);
        return new SimpleResponse(200, "successfully deleted");
    }

    /**
     * Maps a {@link UserDTO} onto a {@link User} entity: loads and mutates the
     * existing entity when {@code id != 0}, otherwise builds a fresh one with a
     * default hashed password.
     */
    private User injectUserDTO(UserDTO user, Long id) {
        if (id != 0) {
            Optional<User> result = userRepository.findById(id);
            result.get().setId(id);
            result.get().setUsername(user.getName());
            result.get().setDisplayName(user.getDisplayName());
            result.get().setEmail(user.getEmail());
            result.get().setStatus(user.getActive());
            result.get().setIsEnabled(user.getIsEnabled());
            return result.get();
        } else {
            User u = new User();
            u.setUsername(user.getName());
            u.setDisplayName(user.getDisplayName());
            u.setEmail(user.getEmail());
            u.setStatus(user.getActive());
            u.setPassword(authenticationService.generatePass(user.getInitialPassword()));
            u.setIsEnabled((byte) 1);
            return u;
        }

    }

}

package com.gsgd.generic_erp.service.admin;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.dto.UserAccessDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserNavMenu;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.admin.UserNavMenuRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.spec.UserSpecification;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

/**
 * Backs the "Menu Access" screens: for a given navigation menu, list every
 * user (or every permission held by one user) alongside whether they
 * currently have access, and grant/revoke that access.
 *
 * Access is recorded directly on user_nav_menu. A row with permission_id =
 * null is baseline "this user can open this menu" access; a row with a
 * permission_id scopes that access to one specific permission. Either kind
 * of row makes the menu visible to the user (see UserService#fetchNavMenu),
 * so granting a specific permission implicitly grants baseline access too.
 */
@Service
public class MenuAccessService {

    private final UserNavMenuRepository userNavMenuRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public MenuAccessService(UserNavMenuRepository userNavMenuRepository, UserRepository userRepository,
            PermissionRepository permissionRepository) {
        this.userNavMenuRepository = userNavMenuRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<UserAccessDTO> listUsersForMenu(Long navId) {
        Set<Long> grantedUserIds = userNavMenuRepository.findByNavIdAndIsEnabledTrue(navId).stream()
                .map(UserNavMenu::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAll(UserSpecification.excludeDisabled((byte) 1)).stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new UserAccessDTO(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                        grantedUserIds.contains(u.getId())))
                .toList();
    }

    public List<PermissionAccessDTO> listPermissionsForMenu(Long navId, Long userId) {
        Set<Long> grantedPermissionIds = userNavMenuRepository.findByNavIdAndUserIdAndIsEnabledTrue(navId, userId)
                .stream()
                .map(UserNavMenu::getPermissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getPermissionName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new PermissionAccessDTO(p.getId(), p.getPermissionName(), p.getVal(),
                        grantedPermissionIds.contains(p.getId())))
                .toList();
    }

    @Transactional
    public SimpleResponse setUserAccess(Long navId, Long userId, boolean granted) {
        List<UserNavMenu> rows = userNavMenuRepository.findByNavIdAndUserId(navId, userId);
        if (granted) {
            UserNavMenu baseline = rows.stream().filter(r -> r.getPermissionId() == null).findFirst().orElse(null);
            if (baseline == null) {
                userNavMenuRepository.save(UserNavMenu.builder()
                        .navId(navId)
                        .userId(userId)
                        .permissionId(null)
                        .isEnabled(true)
                        .createDate(LocalDateTime.now())
                        .build());
            } else if (!Boolean.TRUE.equals(baseline.getIsEnabled())) {
                baseline.setIsEnabled(true);
                userNavMenuRepository.save(baseline);
            }
        } else if (!rows.isEmpty()) {
            // Revoking menu access revokes every permission scoped to this menu too.
            userNavMenuRepository.deleteAll(rows);
        }
        return new SimpleResponse(200, "");
    }

    @Transactional
    public SimpleResponse setPermissionAccess(Long navId, Long userId, Long permissionId, boolean granted) {
        List<UserNavMenu> rows = userNavMenuRepository.findByNavIdAndUserIdAndPermissionId(navId, userId,
                permissionId);
        if (granted) {
            if (rows.isEmpty()) {
                userNavMenuRepository.save(UserNavMenu.builder()
                        .navId(navId)
                        .userId(userId)
                        .permissionId(permissionId)
                        .isEnabled(true)
                        .createDate(LocalDateTime.now())
                        .build());
            } else {
                rows.forEach(r -> r.setIsEnabled(true));
                userNavMenuRepository.saveAll(rows);
            }
        } else if (!rows.isEmpty()) {
            userNavMenuRepository.deleteAll(rows);
        }
        return new SimpleResponse(200, "");
    }
}

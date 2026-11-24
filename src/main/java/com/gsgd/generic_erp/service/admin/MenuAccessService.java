package com.gsgd.generic_erp.service.admin;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.dto.PermissionRegistrationDTO;
import com.gsgd.generic_erp.dto.UserAccessDTO;
import com.gsgd.generic_erp.entity.auth.MenuRegisteredPermissionsRecord;
import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserNavMenu;
import com.gsgd.generic_erp.repository.admin.MenuRegisteredPermissionRecordRepository;
import com.gsgd.generic_erp.repository.admin.PagePermissinApprovalRecordRepository;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.repository.admin.UserNavMenuRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.spec.UserSpecification;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

/**
 * Backs the "Menu Access" screens: for a given navigation menu, list every user
 * (or every permission held by one user) alongside whether they currently have
 * access, and grant/revoke that access.
 *
 * Access paths (see UserService#fetchNavMenu): - Direct: a row in
 * user_nav_menu grants a user baseline access to a menu. - Via permission: a
 * permission must first be registered against a menu
 * (menu_registered_permissions_record). A user holds it by default when their
 * department role has it; a row in page_permission_approvals_record overrides
 * that default for the user on this page (1 = grant, 0 = remove). Only
 * registered permissions are ever offered.
 *
 * Revoking a user's direct access also clears their permission approvals for
 * that menu — otherwise they'd keep seeing the menu through the other path,
 * which would make "revoke" a no-op from the admin's point of view.
 */
@Service
public class MenuAccessService {

    private final UserNavMenuRepository userNavMenuRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRegisteredPermissionRecordRepository registeredPermissionRepository;
    private final PagePermissinApprovalRecordRepository approvalRepository;
    private final PermissionChecker permissionChecker;
    private final com.gsgd.generic_erp.repository.admin.NavigationMenuRepository menus;

    public MenuAccessService(UserNavMenuRepository userNavMenuRepository, UserRepository userRepository,
            PermissionRepository permissionRepository,
            MenuRegisteredPermissionRecordRepository registeredPermissionRepository,
            PagePermissinApprovalRecordRepository approvalRepository, PermissionChecker permissionChecker,
            com.gsgd.generic_erp.repository.admin.NavigationMenuRepository menus) {
        this.userNavMenuRepository = userNavMenuRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.registeredPermissionRepository = registeredPermissionRepository;
        this.approvalRepository = approvalRepository;
        this.permissionChecker = permissionChecker;
        this.menus = menus;
    }

    /**
     * Every enabled user, flagged with whether they hold direct access to this
     * menu.
     */
    public List<UserAccessDTO> listUsersForMenu(Long navId) {
        Set<Long> grantedUserIds = userNavMenuRepository.findByNavIdAndIsEnabledTrue(navId).stream()
                .map(UserNavMenu::getUserId).collect(Collectors.toSet());
        return userRepository.findAll(UserSpecification.excludeDisabled((byte) 1)).stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new UserAccessDTO(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                        grantedUserIds.contains(u.getId())))
                .toList();
    }

    /**
     * Permissions registered against this menu, each flagged with whether the given
     * user holds it now, whether their department gives it by default, and
     * whether this page overrides the default. Unregistered permissions are never
     * offered.
     */
    public List<PermissionAccessDTO> listPermissionsForMenu(Long navId, Long userId) {
        Set<Long> registeredPermissionIds = registeredPermissionRepository.findByMenuId(navId).stream()
                .map(MenuRegisteredPermissionsRecord::getPermissionId).collect(Collectors.toSet());
        if (registeredPermissionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> overrides = new HashMap<>();
        approvalRepository.findByUserIdAndMenuIdAndPermissionIdIn(userId, navId, registeredPermissionIds)
                .forEach(a -> overrides.put(a.getPermissionId(), isGrant(a)));
        Set<Long> defaults = new HashSet<>(permissionRepository.findDefaultPermissionIdsByUserId(userId));
        return permissionRepository.findAllById(registeredPermissionIds).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getIsEnabled()))
                .sorted(Comparator.comparing(Permission::getPermissionCode, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    boolean byDefault = defaults.contains(p.getId());
                    Boolean override = overrides.get(p.getId());
                    return new PermissionAccessDTO(p.getId(), p.getPermissionCode(),
                            override != null ? override : byDefault, byDefault, override != null);
                })
                .toList();
    }

    /**
     * Grants or revokes a user's direct (baseline) access to a menu. Revoking also
     * clears the user's permission overrides for the menu so access does not
     * survive through the other path (see class doc).
     */
    @Transactional
    public SimpleResponse setUserAccess(Long navId, Long userId, boolean granted) {
        if (granted) {
            var menu = menus.findById(navId).orElse(null);
            if (menu == null || !Boolean.TRUE.equals(menu.getIsEnabled()))
                return new SimpleResponse(404, I18n.t("access.error.menuUnavailable"));
            String readCode = "/pmc/order-audit-task".equals(menu.getRoute()) ? "menu.order-audit-task.read"
                    : "/pmc/evidence".equals(menu.getRoute()) ? "menu.evidence.read" : null;
            if (readCode != null && !permissionChecker.has(readCode))
                return new SimpleResponse(403, I18n.t("access.error.cannotGrantMenu"));
        }
        List<UserNavMenu> rows = userNavMenuRepository.findByNavIdAndUserId(navId, userId);
        if (granted) {
            UserNavMenu baseline = rows.stream().filter(r -> r.getPermissionId() == null).findFirst().orElse(null);
            if (baseline == null) {
                userNavMenuRepository.save(UserNavMenu.builder().navId(navId).userId(userId).permissionId(null)
                        .isEnabled(true).createDate(LocalDateTime.now()).build());
            } else if (!Boolean.TRUE.equals(baseline.getIsEnabled())) {
                baseline.setIsEnabled(true);
                userNavMenuRepository.save(baseline);
            }
        } else {
            if (!rows.isEmpty()) {
                userNavMenuRepository.deleteAll(rows);
            }
            List<PagePermissionApprovalsRecord> approvals = approvalRepository.findByUserIdAndMenuId(userId, navId);
            if (!approvals.isEmpty()) {
                approvalRepository.deleteAll(approvals);
            }
        }
        return new SimpleResponse(200, "");
    }

    /**
     * Sets one permission for a user on a menu (idempotent). The department
     * default stays the source of truth: when the requested state equals the
     * default, the page override is removed; otherwise an override row is kept
     * ({@code permission_enabled} 1 = grant, 0 = take the default away).
     */
    @Transactional
    public SimpleResponse setPermissionAccess(Long navId, Long userId, Long permissionId, boolean granted) {
        // Overrides become API authorities, so the permission must be registered on
        // this menu and held by the caller (no granting what you do not have).
        boolean registered = registeredPermissionRepository.findByMenuIdAndPermissionId(navId, permissionId)
                .stream().anyMatch(r -> Byte.valueOf((byte) 1).equals(r.getPermissionEnabled()));
        Permission permission = permissionRepository.findById(permissionId).orElse(null);
        if (!registered || permission == null || !Integer.valueOf(1).equals(permission.getIsEnabled())) {
            return new SimpleResponse(404, I18n.t("access.error.permissionNotOnMenu"));
        }
        if (granted && !permissionChecker.has(permission.getPermissionCode())) {
            return new SimpleResponse(403, I18n.t("access.error.cannotGrantPermission"));
        }
        List<PagePermissionApprovalsRecord> rows = approvalRepository.findByUserIdAndMenuIdAndPermissionId(userId,
                navId, permissionId);
        boolean byDefault = permissionRepository.findDefaultPermissionIdsByUserId(userId).contains(permissionId);
        if (granted == byDefault) {
            if (!rows.isEmpty()) {
                approvalRepository.deleteAll(rows);
            }
            return new SimpleResponse(200, "");
        }
        byte flag = (byte) (granted ? 1 : 0);
        if (rows.isEmpty()) {
            approvalRepository.save(new PagePermissionApprovalsRecord(null, userId, permissionId, navId, flag, null));
        } else {
            PagePermissionApprovalsRecord keep = rows.get(0);
            keep.setPermissionEnabled(flag);
            approvalRepository.save(keep);
            if (rows.size() > 1) {
                approvalRepository.deleteAll(rows.subList(1, rows.size()));
            }
        }
        return new SimpleResponse(200, "");
    }

    private static boolean isGrant(PagePermissionApprovalsRecord a) {
        return Byte.valueOf((byte) 1).equals(a.getPermissionEnabled());
    }

    /**
     * Every permission, with whether it's currently registered against this menu.
     */
    public List<PermissionRegistrationDTO> listRegistrationsForMenu(Long navId) {
        Set<Long> registeredPermissionIds = registeredPermissionRepository.findByMenuId(navId).stream()
                .map(MenuRegisteredPermissionsRecord::getPermissionId).collect(Collectors.toSet());
        return permissionRepository.findExists().stream()
                .sorted(Comparator.comparing(Permission::getPermissionCode, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new PermissionRegistrationDTO(p.getId(), p.getPermissionCode(),
                        registeredPermissionIds.contains(p.getId())))
                .toList();
    }

    /**
     * Register or de-register a permission against a menu. De-registering also
     * clears every user's approval of that permission for this menu — otherwise a
     * de-registered permission could still silently grant sidebar access via an
     * orphaned approval row that this UI can no longer show or manage.
     */
    @Transactional
    public SimpleResponse setPermissionRegistration(Long navId, Long permissionId, boolean registered) {
        List<MenuRegisteredPermissionsRecord> rows = registeredPermissionRepository.findByMenuIdAndPermissionId(navId,
                permissionId);
        if (registered) {
            Permission permission = permissionRepository.findById(permissionId).orElse(null);
            if (permission == null || !Integer.valueOf(1).equals(permission.getIsEnabled()))
                return new SimpleResponse(404, I18n.t("access.error.permissionUnavailable"));
            if (rows.isEmpty()) {
                registeredPermissionRepository
                        .save(new MenuRegisteredPermissionsRecord(null, navId, permissionId, (byte) 1, null));
            }
        } else {
            if (!rows.isEmpty()) {
                registeredPermissionRepository.deleteAll(rows);
            }
            List<PagePermissionApprovalsRecord> approvals = approvalRepository.findByMenuIdAndPermissionId(navId,
                    permissionId);
            if (!approvals.isEmpty()) {
                approvalRepository.deleteAll(approvals);
            }
        }
        return new SimpleResponse(200, "");
    }
}

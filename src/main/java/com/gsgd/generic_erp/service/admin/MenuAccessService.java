package com.gsgd.generic_erp.service.admin;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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
 * Backs the "Menu Access" screens: for a given navigation menu, list every
 * user (or every permission held by one user) alongside whether they
 * currently have access, and grant/revoke that access.
 *
 * Two independent access paths, either sufficient on its own (see
 * UserService#fetchNavMenu):
 * - Direct: a row in user_nav_menu grants a user baseline access to a menu.
 * - Via permission: a permission must first be registered against a menu
 * (menu_registered_permissions_record) before it can grant anything there;
 * a user then holds it for that menu via a row in
 * page_permission_approvals_record. Only registered permissions are ever
 * offered for approval.
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

    public MenuAccessService(UserNavMenuRepository userNavMenuRepository, UserRepository userRepository,
            PermissionRepository permissionRepository,
            MenuRegisteredPermissionRecordRepository registeredPermissionRepository,
            PagePermissinApprovalRecordRepository approvalRepository) {
        this.userNavMenuRepository = userNavMenuRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.registeredPermissionRepository = registeredPermissionRepository;
        this.approvalRepository = approvalRepository;
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
        Set<Long> registeredPermissionIds = registeredPermissionRepository.findByMenuId(navId).stream()
                .map(MenuRegisteredPermissionsRecord::getPermissionId)
                .collect(Collectors.toSet());
        if (registeredPermissionIds.isEmpty()) {
            return List.of();
        }
        Set<Long> grantedPermissionIds = approvalRepository
                .findByUserIdAndMenuIdAndPermissionIdIn(userId, navId, registeredPermissionIds)
                .stream()
                .map(PagePermissionApprovalsRecord::getPermissionId)
                .collect(Collectors.toSet());
        return permissionRepository.findAllById(registeredPermissionIds).stream()
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

    @Transactional
    public SimpleResponse setPermissionAccess(Long navId, Long userId, Long permissionId, boolean granted) {
        List<PagePermissionApprovalsRecord> rows = approvalRepository.findByUserIdAndMenuIdAndPermissionId(userId,
                navId, permissionId);
        if (granted) {
            if (rows.isEmpty()) {
                approvalRepository.save(new PagePermissionApprovalsRecord(null, userId, permissionId, navId, null));
            }
        } else if (!rows.isEmpty()) {
            approvalRepository.deleteAll(rows);
        }
        return new SimpleResponse(200, "");
    }

    /** Every permission, with whether it's currently registered against this menu. */
    public List<PermissionRegistrationDTO> listRegistrationsForMenu(Long navId) {
        Set<Long> registeredPermissionIds = registeredPermissionRepository.findByMenuId(navId).stream()
                .map(MenuRegisteredPermissionsRecord::getPermissionId)
                .collect(Collectors.toSet());
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getPermissionName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new PermissionRegistrationDTO(p.getId(), p.getPermissionName(), p.getVal(),
                        registeredPermissionIds.contains(p.getId())))
                .toList();
    }

    /**
     * Register or de-register a permission against a menu. De-registering also
     * clears every user's approval of that permission for this menu — otherwise
     * a de-registered permission could still silently grant sidebar access via
     * an orphaned approval row that this UI can no longer show or manage.
     */
    @Transactional
    public SimpleResponse setPermissionRegistration(Long navId, Long permissionId, boolean registered) {
        List<MenuRegisteredPermissionsRecord> rows = registeredPermissionRepository
                .findByMenuIdAndPermissionId(navId, permissionId);
        if (registered) {
            if (rows.isEmpty()) {
                registeredPermissionRepository.save(new MenuRegisteredPermissionsRecord(null, navId, permissionId,
                        null));
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

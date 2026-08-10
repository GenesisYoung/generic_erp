package com.gsgd.generic_erp.controller.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.dto.PermissionRegistrationDTO;
import com.gsgd.generic_erp.dto.UserAccessDTO;
import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.service.admin.MenuAccessService;
import com.gsgd.generic_erp.service.admin.NavigationMenutViewService;
import com.gsgd.generic_erp.util.BasicPageRequest;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.view.sql.NavigationPermissionView;

/**
 * Read-side endpoints ({@code /api/permission}) for the permission-management
 * screens: paginated queries over the menu/permission database view, plus
 * per-menu listings of users, permissions, and registrations with their
 * current grant status. Mutations live in
 * {@link ManipulatePermissionController}.
 */
@RestController
@RequestMapping("/api/permission")
public class PermissionManagementController {

    private NavigationMenutViewService service;
    private MenuAccessService menuAccessService;

    public PermissionManagementController(NavigationMenutViewService service, MenuAccessService menuAccessService) {
        this.service = service;
        this.menuAccessService = menuAccessService;
    }

    /** Paginated, filterable query over the navigation/permission view. */
    @PostMapping("/fetch")
    public BasicPageResponse<NavigationPermissionView, NavigationPermissionView> fetchPermissionThrouthUser(
            @RequestBody BasicPageRequest<PermissionMenuViewFilter> filter) {
        return service.query(filter);
    }

    @PostMapping("/fetch/default")
    public BasicPageResponse<NavigationPermissionView, NavigationPermissionView> fetchDefaults(
            @RequestBody BasicPageRequest<PermissionMenuViewFilter> filter) {
        return service.query(filter);
    }

    @GetMapping("/fetch/menu/permission")
    public BasicResponse getMethodName() {
        return service.fetchOnlyMenu();
    }

    /** Every user, with whether they currently have access to this menu. */
    @GetMapping("/menu/{navId}/users")
    public List<UserAccessDTO> fetchUsersForMenu(@PathVariable Long navId) {
        return menuAccessService.listUsersForMenu(navId);
    }

    /** Every permission, with whether the given user holds it for this menu. */
    @GetMapping("/menu/permission")
    public List<PermissionAccessDTO> fetchPermissionsForMenu(@RequestParam Long navId,
            @RequestParam Long userId) {
        return menuAccessService.listPermissionsForMenu(navId, userId);
    }

    /**
     * Every permission, with whether it's currently registered against this menu.
     */
    @GetMapping("/menu/registration")
    public List<PermissionRegistrationDTO> fetchRegistrationsForMenu(@RequestParam Long navId) {
        return menuAccessService.listRegistrationsForMenu(navId);
    }
}
package com.gsgd.generic_erp.controller.auth;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.security.RoleGuard;
import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.service.auth.UserService;
import com.gsgd.generic_erp.util.BasicResponse;

/**
 * User-facing endpoints ({@code /api/users}) for data belonging to the
 * authenticated user, e.g. the sidebar navigation menu.
 */
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/users")
public class UserController {

    private UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * The caller's effective permission codes, so the frontend can hide routes
     * and actions it would be refused anyway. Purely cosmetic: the backend still
     * checks every request ({@code PermissionChecker}).
     */
    @RequestMapping(path = "/permissions", method = RequestMethod.GET)
    public BasicResponse fetchPermissions(@AuthenticationPrincipal UserDetail me) {
        List<String> codes = me.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && !a.startsWith("ROLE_"))
                .sorted()
                .toList();
        boolean root = me.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + RoleGuard.ROOT_ROLE).equals(a.getAuthority()));
        return new BasicResponse(200, "Permissions", new PermissionSet(root, codes));
    }

    /** Returns all sidebar menu items visible to the given user. */
    @RequestMapping(path = "/fetch/sidebar/menu", method = RequestMethod.GET)
    public BasicResponse fetchSideMenu(@AuthenticationPrincipal UserDetail me) {
        return service.fetchNavMenu(me.getUser().getId());
    }
}

/** The caller's permission codes; {@code root} passes every check. */
record PermissionSet(boolean root, List<String> codes) {
}

package com.gsgd.generic_erp.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.service.admin.MenuAccessService;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Write-side endpoints ({@code /api/permission/manipulate}) for granting and
 * revoking menu access: user-level access, per-user permission grants, and
 * permission-to-menu registrations. Read queries live in
 * {@link PermissionManagementController}.
 */
@RestController
@RequestMapping("/api/permission/manipulate")
public class ManipulatePermissionController {

    private final MenuAccessService service;

    public ManipulatePermissionController(MenuAccessService service) {
        this.service = service;
    }

    /** Grant or revoke a user's baseline access to a menu. */
    @PostMapping("/user")
    public SimpleResponse setUserAccess(@RequestBody UserAccessRequest request) {
        try {
            return service.setUserAccess(request.navId(), request.userId(), request.granted());
        } catch (Exception e) {
            return new SimpleResponse(500, e.getMessage());
        }
    }

    /** Grant or revoke one permission for a user, scoped to a menu. */
    @PostMapping("/permission")
    public SimpleResponse setPermissionAccess(@RequestBody PermissionAccessRequest request) {
        try {
            return service.setPermissionAccess(request.navId(), request.userId(), request.permissionId(),
                    request.granted());
        } catch (Exception e) {
            return new SimpleResponse(500, e.getMessage());
        }
    }

    /** Register or de-register a permission against a menu. */
    @PostMapping("/registration")
    public SimpleResponse setPermissionRegistration(@RequestBody PermissionRegistrationRequest request) {
        try {
            return service.setPermissionRegistration(request.navId(), request.permissionId(),
                    request.registered());
        } catch (Exception e) {
            return new SimpleResponse(500, e.getMessage());
        }
    }

    @GetMapping("/defaults")
    public String getMethodName(@RequestParam String param) {
        return new String();
    }

}

/** Request body: grant/revoke a user's access to a menu. */
record UserAccessRequest(Long navId, Long userId, boolean granted) {
}

/** Request body: grant/revoke one permission for a user on a menu. */
record PermissionAccessRequest(Long navId, Long userId, Long permissionId, boolean granted) {
}

/** Request body: register/de-register a permission against a menu. */
record PermissionRegistrationRequest(Long navId, Long permissionId, boolean registered) {
}

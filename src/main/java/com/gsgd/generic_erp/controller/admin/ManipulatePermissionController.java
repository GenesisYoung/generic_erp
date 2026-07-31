package com.gsgd.generic_erp.controller.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.service.admin.MenuAccessService;
import com.gsgd.generic_erp.util.SimpleResponse;

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
}

record UserAccessRequest(Long navId, Long userId, boolean granted) {
}

record PermissionAccessRequest(Long navId, Long userId, Long permissionId, boolean granted) {
}

record PermissionRegistrationRequest(Long navId, Long permissionId, boolean registered) {
}

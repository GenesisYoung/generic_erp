package com.gsgd.generic_erp.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.PermissionDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.service.admin.PermissionService;
import com.gsgd.generic_erp.util.BasicPageResponse;

@RestController
@RequestMapping("/api/permission")
public class PermissionManagementController {

    private final PermissionService permissionService;

    public PermissionManagementController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/permissions")
    public BasicPageResponse<Permission, PermissionDTO> fetchPermissions() {
        return null;
    }
}

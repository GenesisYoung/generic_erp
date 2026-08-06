package com.gsgd.generic_erp.controller.admin;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.PermissionDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.service.admin.PermissionService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Permission CRUD endpoints ({@code /api/admin/permissions}):
 * paginated listing with optional name filter, save/update, batch deletion,
 * and cascading deletion of a root permission together with its children.
 */
@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** Returns a page of permissions, optionally filtered by name. */
    @GetMapping("/fetch")
    public BasicPageResponse<Permission, PermissionDTO> getMethodName(int page, int size,
            @RequestParam(required = false) String name) {
        BasicPage p = new BasicPage(page, size);
        // Stable ordering: by permission code, newest first within the same code
        p.defineSort(Sort.by("permissionCode").ascending().and(Sort.by("createDate").descending()));
        return permissionService.getAllPermissions(p, name);
    }

    /** Deletes permissions by id; returns the number of rows removed. */
    @PostMapping("/delete")
    public BasicResponse deleteByVal(@RequestBody DeleteRecord record) {

        long b = permissionService.deleteByGroup(record.deleteVal());
        return new BasicResponse(200, "success", b);

    }

    /**
     * Deletes root permissions by code, first removing all of their
     * sub-permissions so no orphaned children remain.
     */
    @PostMapping("/deleteRoot")
    public BasicResponse deleteRoot(@RequestBody DeleteRootRecord record) {

        permissionService.findAndDeleteSubPermission(record.deleteVal());
        long b = permissionService.deleteByGroup(record.deleteVal());
        return new BasicResponse(200, "success", b);

    }

    /** Creates or updates a permission from the submitted DTO. */
    @PostMapping("/save")
    public SimpleResponse postMethodName(@RequestBody PermissionDTO entity) {
        return permissionService.saveOrUpdate(permissionService.transferObj(entity));
    }

}

/** Request body carrying permission ids to delete. */
record DeleteRecord(Long[] deleteVal) {
}

/**
 * Request body carrying root permission codes to delete (with their children).
 */
record DeleteRootRecord(String[] deleteVal) {
}

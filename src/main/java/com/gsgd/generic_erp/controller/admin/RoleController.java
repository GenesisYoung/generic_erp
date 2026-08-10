package com.gsgd.generic_erp.controller.admin;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.dto.PermissionAccessDTO;
import com.gsgd.generic_erp.dto.RoleDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.service.admin.RoleManagementService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Endpoints backing the "Default permission allocation" screen
 * ({@code /api/admin/roles}): paginated role CRUD, and the per-role
 * associated-permission roster with grant/revoke.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleManagementService service;

    public RoleController(RoleManagementService service) {
        this.service = service;
    }

    /** A page of roles (20 per page by default), newest last by id. */
    @GetMapping("/fetch")
    public BasicPageResponse<Role, RoleDTO> fetchRoles(int page, int size) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("id").ascending());
        return service.fetchRoles(p);
    }

    /** Creates a role (no id) or updates an existing one. */
    @PostMapping("/save")
    public SimpleResponse save(@RequestBody RoleDTO role) {
        return service.saveOrUpdate(role);
    }

    /**
     * Deletes a role. When users are still assigned to it the deletion is
     * refused: this returns HTTP 200 carrying {@code code = 423} so the frontend
     * can show the reason without tripping the axios refresh-and-retry path that
     * fires on non-2xx responses.
     */
    @DeleteMapping("/delete/{id}")
    public SimpleResponse delete(@PathVariable Long id) {
        try {
            return service.deleteRole(id);
        } catch (NoneDeleteableException e) {
            return new SimpleResponse(423, e.getMessage());
        }
    }

    /** A page of permissions flagged with whether this role holds each one. */
    @GetMapping("/{roleId}/permissions")
    public BasicPageResponse<Permission, PermissionAccessDTO> permissionsForRole(@PathVariable Long roleId,
            int page, int size) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("permissionCode").ascending());
        return service.listPermissionsForRole(roleId, p);
    }

    /** Grants or revokes a single permission for a role. */
    @PostMapping("/permission")
    public SimpleResponse setPermission(@RequestBody RolePermissionRequest request) {
        return service.setRolePermission(request.roleId(), request.permissionId(), request.granted());
    }
}

/** Request body: grant/revoke one permission for a role. */
record RolePermissionRequest(Long roleId, Long permissionId, boolean granted) {
}

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
import com.gsgd.generic_erp.dto.DepartmentDTO;
import com.gsgd.generic_erp.dto.RoleGrantDTO;
import com.gsgd.generic_erp.entity.auth.Department;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.service.admin.DepartmentManagementService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Endpoints backing the "Department Management" screen
 * ({@code /api/admin/departments}): paginated department CRUD, and the
 * per-department associated-role roster with link/unlink.
 */
@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentController {

    private final DepartmentManagementService service;

    public DepartmentController(DepartmentManagementService service) {
        this.service = service;
    }

    /** A page of departments (20 per page by default), ordered by id. */
    @GetMapping("/fetch")
    public BasicPageResponse<Department, DepartmentDTO> fetchDepartments(int page, int size) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("id").ascending());
        return service.fetchDepartments(p);
    }

    /** Creates a department (no id) or updates an existing one. */
    @PostMapping("/save")
    public SimpleResponse save(@RequestBody DepartmentDTO department) {
        return service.saveOrUpdate(department);
    }

    /**
     * Deletes a department. When users are still assigned to it the deletion is
     * refused: this returns HTTP 200 carrying {@code code = 423} so the frontend
     * can show the reason without tripping the axios refresh-and-retry path that
     * fires on non-2xx responses.
     */
    @DeleteMapping("/delete/{id}")
    public SimpleResponse delete(@PathVariable Long id) {
        try {
            return service.deleteDepartment(id);
        } catch (NoneDeleteableException e) {
            return new SimpleResponse(423, e.getMessage());
        }
    }

    /** A page of roles flagged with whether this department is linked to each. */
    @GetMapping("/{deptId}/roles")
    public BasicPageResponse<Role, RoleGrantDTO> rolesForDepartment(@PathVariable Long deptId,
            int page, int size) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("id").ascending());
        return service.listRolesForDepartment(deptId, p);
    }

    /** Links or unlinks a single role to a department. */
    @PostMapping("/role")
    public SimpleResponse setRole(@RequestBody DepartmentRoleRequest request) {
        return service.setDepartmentRole(request.deptId(), request.roleId(), request.granted());
    }

    @GetMapping("/dept/options")
    public BasicResponse fetchDepts() {
        return service.fetDepts();
    }
}

/** Request body: link/unlink one role to a department. */
record DepartmentRoleRequest(Long deptId, Long roleId, boolean granted) {
}

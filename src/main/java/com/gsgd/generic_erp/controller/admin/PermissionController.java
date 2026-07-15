package com.gsgd.generic_erp.controller.admin;

import java.util.List;

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
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/fetch")
    public BasicResponse getMethodName(int page, int size, @RequestParam(required = false) String name) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("createDate").descending());
        List<Permission> permissions = permissionService.getAllPermissions(p, name);
        return new BasicResponse(200, "Success", permissionService.transferDTO(permissions));

    }

    @PostMapping("/save")
    public SimpleResponse postMethodName(@RequestBody PermissionDTO entity) {
        permissionService.saveOrUpdate(permissionService.transferObj(entity));
        return null;
    }

}

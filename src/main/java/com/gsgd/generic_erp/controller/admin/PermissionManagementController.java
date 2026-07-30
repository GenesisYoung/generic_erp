package com.gsgd.generic_erp.controller.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.service.admin.NavigationMenutViewService;
import com.gsgd.generic_erp.util.BasicPageRequest;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.view.NavigationPermissionView;

@RestController
@RequestMapping("/api/permission")
public class PermissionManagementController {

    private NavigationMenutViewService service;

    public PermissionManagementController(NavigationMenutViewService service) {
        this.service = service;
    }

    @PostMapping("/fetch")
    public BasicPageResponse<NavigationPermissionView, NavigationPermissionView> fetchPermissionThrouthUser(
            @RequestBody BasicPageRequest<PermissionMenuViewFilter> filter) {
        return service.query(filter);
    }
}
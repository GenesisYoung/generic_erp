package com.gsgd.generic_erp.service.admin;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.repository.admin.NavigationPermissionViewRepository;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.spec.NavigationMenuSpecification;
import com.gsgd.generic_erp.spec.PermissionSpecification;
import com.gsgd.generic_erp.util.BasicPageRequest;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.view.sql.NavigationPermissionView;

/**
 * Read-only queries over the {@link NavigationPermissionView} database view,
 * which joins menus, users, and permissions for the permission-management UI.
 */
@Service
public class NavigationMenutViewService {
    private NavigationPermissionViewRepository repository;

    private PermissionRepository permissionRepository;

    public NavigationMenutViewService(NavigationPermissionViewRepository repo,
            PermissionRepository permissionRepository) {
        this.repository = repo;
        this.permissionRepository = permissionRepository;
    }

    /** Paginated, specification-filtered query over the view. */
    public BasicPageResponse<NavigationPermissionView, NavigationPermissionView> query(
            BasicPageRequest<PermissionMenuViewFilter> filter) {
        Page<NavigationPermissionView> p = repository.findAll(
                NavigationMenuSpecification.findByFilter(filter.getFilter()),
                PageRequest.of(filter.getPage(), filter.getSize()));
        return new BasicPageResponse<>(p.getContent(), p);
    }

    public BasicResponse fetchOnlyMenu() {
        List<Permission> menuPermissions = permissionRepository.findAll(PermissionSpecification.findMenuPermission());
        return new BasicResponse(200, "success", menuPermissions);
    }
}

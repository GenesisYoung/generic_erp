package com.gsgd.generic_erp.service.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.repository.admin.NavigationPermissionViewRepository;
import com.gsgd.generic_erp.spec.NavigationMenuSpecification;
import com.gsgd.generic_erp.util.BasicPageRequest;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.view.NavigationPermissionView;

@Service
public class NavigationMenutViewService {
    private NavigationPermissionViewRepository repository;

    public NavigationMenutViewService(NavigationPermissionViewRepository repo) {
        this.repository = repo;
    }

    public BasicPageResponse<NavigationPermissionView, NavigationPermissionView> query(
            BasicPageRequest<PermissionMenuViewFilter> filter) {
        Page<NavigationPermissionView> p = repository.findAll(
                NavigationMenuSpecification.findByFilter(filter.getFilter()),
                PageRequest.of(filter.getPage(), filter.getSize()));
        return new BasicPageResponse<>(p.getContent(), p);
    }
}

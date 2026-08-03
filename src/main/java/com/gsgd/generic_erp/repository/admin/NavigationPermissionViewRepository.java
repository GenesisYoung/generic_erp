package com.gsgd.generic_erp.repository.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.view.NavigationPermissionView;

/** Read-only repository over the navigation/permission database view. */
public interface NavigationPermissionViewRepository
        extends JpaRepository<NavigationPermissionView, Long>, JpaSpecificationExecutor<NavigationPermissionView> {
}

package com.gsgd.generic_erp.repository.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.auth.NavigationMenu;

public interface NavigationMenuRepository
        extends JpaRepository<NavigationMenu, Long>, JpaSpecificationExecutor<NavigationMenu> {
}

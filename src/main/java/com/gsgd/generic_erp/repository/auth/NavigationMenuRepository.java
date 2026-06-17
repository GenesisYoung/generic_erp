package com.gsgd.generic_erp.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.NavigationMenu;

public interface NavigationMenuRepository extends JpaRepository<NavigationMenu, Long> {
}

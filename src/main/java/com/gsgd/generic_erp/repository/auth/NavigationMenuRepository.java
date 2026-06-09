package com.gsgd.generic_erp.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gsgd.generic_erp.entity.auth.NavigationMenu;

@Repository
public interface NavigationMenuRepository extends JpaRepository<NavigationMenu, Long> {
}

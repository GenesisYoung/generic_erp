package com.gsgd.generic_erp.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

import com.gsgd.generic_erp.entity.auth.UserNavMenu;

public interface UserNavMenuRepository extends JpaRepository<UserNavMenu, Long> {

    @NativeQuery("SELECT * FROM user_nav_menu AS m WHERE m.user_id = ?1")
    List<UserNavMenu> findByUserId(Long id);
}
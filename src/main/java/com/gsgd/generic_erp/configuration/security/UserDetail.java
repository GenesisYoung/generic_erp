package com.gsgd.generic_erp.configuration.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.gsgd.generic_erp.entity.user.Role;
import com.gsgd.generic_erp.entity.user.User;
import com.gsgd.generic_erp.entity.user.UserRole;
import com.gsgd.generic_erp.repository.user.RoleRepository;
import com.gsgd.generic_erp.repository.user.UserRoleRepository;

public class UserDetail implements UserDetails{


    private final User user;
    private final List<Role> roles;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;

    public UserDetail(User user) {
        this.user = user;
        roles= convertToRoles(userRoleRepository.findByUserId(user.getId()));
    }

    private List<Role> convertToRoles(List<UserRole> byUserId) {
        if (byUserId == null || byUserId.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Role> roles = new java.util.ArrayList<>();
        for (UserRole userRole : byUserId) {
            Role role = roleRepository.findById(userRole.getRoleId()).orElse(null);
            if (role != null) {
                roles.add(role);
            }
        }
       
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
        .map(role->new SimpleGrantedAuthority("ROLE_"+role.getRoleName()))
        .collect(Collectors.toList());
    }

    @Override
    public @Nullable String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
    
}

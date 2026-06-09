package com.gsgd.generic_erp.configuration.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;

// This class implements the UserDetails interface 
// from Spring Security. It serves as a bridge between 
// our application's User entity and the user details 
// required by Spring Security for authentication and 
// authorization. It encapsulates the user's information 
// and their associated roles, which are converted into 
// GrantedAuthority objects that Spring Security can use 
// to make access control decisions.
public class UserDetail implements UserDetails {

    private final User user;
    private final List<Role> roles;

    public UserDetail(User user, List<Role> roles) {
        this.user = user;
        this.roles = roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleName()))
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

package com.gsgd.generic_erp.configuration.security.impl;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.entity.user.Role;
import com.gsgd.generic_erp.entity.user.User;
import com.gsgd.generic_erp.entity.user.UserRole;
import com.gsgd.generic_erp.repository.user.RoleRepository;
import com.gsgd.generic_erp.repository.user.UserRepository;
import com.gsgd.generic_erp.repository.user.UserRoleRepository;

// This service is responsible for loading user details during authentication. 
// It retrieves the user from the database based on the provided username, and 
// then fetches the associated roles to construct a UserDetail object that 
// Spring Security can use for authentication and authorization decisions.
@Service
public class CustomizedUserDetailServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public CustomizedUserDetailServiceImpl(UserRepository userRepository,
                                           UserRoleRepository userRoleRepository,
                                           RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    // This method is called by Spring Security during the authentication process.
    // It takes a username as input, retrieves the corresponding User entity from 
    // the database, and then fetches the user's roles. Finally, it constructs and 
    // returns a UserDetail object that contains the user's information and authorities 
    // (roles) for Spring Security to use in authentication and authorization.
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        List<Role> roles = convertToRoles(userRoleRepository.findByUserId(user.getId()));
        return new UserDetail(user,roles);
    }
    // Helper method to convert a list of UserRole entities to a list of Role entities.
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
    
}

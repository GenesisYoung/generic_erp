package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.configuration.security.impl.AuthenticationImpl;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

@Service
public class UserManagementService {
    private final JWTUtil JWTUtil;
    private UserRepository repository;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private UserRepository userRepository;
    private AuthenticationImpl authenticationService;

    public UserManagementService(UserRepository repository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository, JWTUtil JWTUtil,
            AuthenticationImpl aService) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.JWTUtil = JWTUtil;
        this.authenticationService = aService;
    }

    public BasicPageResponse<User, UserDTO> fetchUserList(Pageable pageable) {
        Page<User> users = repository.findAll(pageable);
        List<UserDTO> dto = new ArrayList<>();
        users.stream().forEach((User ele) -> {
            dto.add(new UserDTO(ele.getId(), ele.getUsername(), ele.getEmail(), ele.getDisplayName(),
                    roleRepository.findByUserId(ele.getId()), ele.getStatus()));
        });
        ;
        return new BasicPageResponse<>(dto, users);
    }

    public SimpleResponse saveOrUpdate(Long userId, UserDTO user) {
        try {
            if (userId == 0) {
                User saved = repository.save(injectUserDTO(user, userId));
                for (Integer role : user.getRoles()) {
                    Long id = roleRepository.getIdByVal(role);
                    userRoleRepository.save(new UserRole(null, saved.getId(), id, LocalDate.now()));
                }
                return new SimpleResponse(200, "Successfully created");
            } else {
                repository.saveAndFlush(injectUserDTO(user, userId));
                // Latest roles
                List<Role> latestRoles = roleRepository.findObjByValue(user.getRoles());
                // Current roles
                List<UserRole> uRoles = userRoleRepository.findByUserId(userId);
                List<Long> ids = uRoles.stream().map(ele -> ele.getRoleId()).toList();
                List<Role> currentRoles = roleRepository
                        .findObjByIds(ids);
                for (Role r : currentRoles) {
                    if (!latestRoles.contains(r)) {
                        userRoleRepository.deleteByUserIdAndRoleId(userId, r.getId());
                    } else if (currentRoles.contains(r)) {
                        latestRoles.remove(r);
                    }
                }
                for (Role r : latestRoles) {
                    UserRole userRole = new UserRole();
                    userRole.setRoleId(r.getId());
                    userRole.setUserId(userId);
                    userRoleRepository.save(userRole);
                }
                return new SimpleResponse(200, "Successfully created");
            }
        } catch (Exception e) {

            return new SimpleResponse(201, "Failed to save" + e.getMessage());
        }
    }

    private User injectUserDTO(UserDTO user, Long id) {
        if (id != 0) {
            Optional<User> result = userRepository.findById(id);
            if (id != 0)
                result.get().setId(id);
            result.get().setUsername(user.getName());
            result.get().setDisplayName(user.getDisplayName());
            result.get().setEmail(user.getEmail());
            result.get().setStatus(user.getActive());
            return result.get();
        } else {
            User u = new User();
            u.setUsername(user.getName());
            u.setDisplayName(user.getDisplayName());
            u.setEmail(user.getEmail());
            u.setStatus(user.getActive());
            u.setPassword(authenticationService.generatePass("userpass"));
            return u;
        }

    }

}
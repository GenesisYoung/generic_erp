package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.impl.AuthenticationImpl;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserRole;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.auth.UserRoleRepository;
import com.gsgd.generic_erp.spec.UserSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

@Service
public class UserManagementService {
    // private final JWTUtil JWTUtil;
    private UserRepository repository;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private UserRepository userRepository;
    private AuthenticationImpl authenticationService;

    public UserManagementService(UserRepository repository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository,
            AuthenticationImpl aService) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        // this.JWTUtil = JWTUtil;
        this.authenticationService = aService;
    }

    public BasicPageResponse<User, UserDTO> fetchUserList(Pageable pageable) {
        Page<User> users = repository.findAll(UserSpecification.excludeDisabled((byte) 1), pageable);
        List<UserDTO> dto = new ArrayList<>();
        users.stream().forEach((User ele) -> {
            dto.add(new UserDTO(ele.getId(), ele.getUsername(), ele.getEmail(), ele.getDisplayName(),
                    roleRepository.findByUserId(ele.getId()), ele.getStatus(), ele.getIsEnabled()));
        });
        ;
        return new BasicPageResponse<>(dto, users);
    }

    @Transactional
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
                // 1. Resolve the latest roles to a Set of IDs (one query)
                @SuppressWarnings("null")
                Set<Long> latestIds = roleRepository.findObjByValue(user.getRoles())
                        .stream()
                        .map(Role::getId)
                        .collect(Collectors.toSet());

                // 2. Resolve the current role IDs (one query)
                @SuppressWarnings("null")
                Set<Long> currentIds = userRoleRepository.findByUserId(userId)
                        .stream()
                        .map(UserRole::getRoleId)
                        .collect(Collectors.toSet());

                // 3. Compute the diff
                // toRemove = current - latest
                Set<Long> toRemove = new HashSet<>(currentIds);
                toRemove.removeAll(latestIds);

                // toAdd = latest - current
                Set<Long> toAdd = new HashSet<>(latestIds);
                toAdd.removeAll(currentIds);

                // 4. Bulk delete (one query)
                if (!toRemove.isEmpty()) {
                    userRoleRepository.deleteByUserIdAndRoleIdIn(userId, toRemove);
                }

                // 5. Bulk insert (one query)
                if (!toAdd.isEmpty()) {
                    List<UserRole> newLinks = toAdd.stream()
                            .map(roleId -> {
                                UserRole ur = new UserRole();
                                ur.setUserId(userId);
                                ur.setRoleId(roleId);
                                return ur;
                            })
                            .toList();
                    userRoleRepository.saveAll(newLinks);
                }

                repository.save(injectUserDTO(user, userId));

                return new SimpleResponse(200, "Successfully created");
            }
        } catch (Exception e) {
            Logger.getGlobal().log(Level.SEVERE, "User saving error", e);
            Logger.getGlobal().info("User saving error--->" + e.getMessage());
            return new SimpleResponse(201, "Failed to save" + e.getMessage());
        }
    }

    @Transactional
    public SimpleResponse deleteUser(Long id) {
        try {
            userRepository.updateIsEnabled(id, (byte) 0);
        } catch (Exception e) {
            if (id == 1) {
                Logger.getGlobal().info("User saving error--->" + e.getMessage());
                return new SimpleResponse(202, "Cannot delete root user");
            }
            return new SimpleResponse(201, "failed to delete user");
        }
        return new SimpleResponse(200, "successfully deleted");
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
            result.get().setIsEnabled(user.getIsEnabled());
            return result.get();
        } else {
            User u = new User();
            u.setUsername(user.getName());
            u.setDisplayName(user.getDisplayName());
            u.setEmail(user.getEmail());
            u.setStatus(user.getActive());
            u.setPassword(authenticationService.generatePass("userpass"));
            u.setIsEnabled((byte) 1);
            return u;
        }

    }

}
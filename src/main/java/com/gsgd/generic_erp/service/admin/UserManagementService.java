package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
    private UserRepository repository;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;

    public UserManagementService(UserRepository repository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
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
                User saved = repository.saveAndFlush(injectUserDTO(user, userId));
                List<Role> existedRoles = roleRepository.findIdsByValue(user.getRoles(), saved.getId());
                user.getRoles().forEach(ele -> {
                    existedRoles.forEach(role -> {
                        if (!user.getRoles().contains(role.getVal())) {
                            UserRole userRole = new UserRole();
                            userRole.setRoleId(role.getId());
                            userRole.setUserId(saved.getId());
                            userRoleRepository.save(userRole);
                        } else {
                            UserRole target = userRoleRepository.findByBothRoleAndUser(saved.getId(), role.getId());
                            userRoleRepository.delete(target);
                        }
                    });
                });
                return new SimpleResponse(200, "Successfully created");
            }
        } catch (Exception e) {
            return new SimpleResponse(201, "Failed to save");
        }
    }

    private User injectUserDTO(UserDTO user, Long id) {
        User result = new User();
        if (id != 0)
            result.setId(id);
        result.setUsername(user.getName());
        result.setDisplayName(user.getDisplayName());
        result.setEmail(user.getEmail());
        result.setStatus(true);
        return result;
    }

}

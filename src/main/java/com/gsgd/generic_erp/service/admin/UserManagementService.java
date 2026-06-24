package com.gsgd.generic_erp.service.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.util.BasicPageResponse;

@Service
public class UserManagementService {
    private UserRepository repository;
    private RoleRepository roleRepository;

    public UserManagementService(UserRepository repository, RoleRepository roleRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
    }

    public BasicPageResponse<User, UserDTO> fetchUserList(Pageable pageable) {
        Page<User> users = repository.findAll(pageable);
        List<UserDTO> dto = new ArrayList<>();
        users.stream().forEach((User ele) -> {
            dto.add(new UserDTO(ele.getId(), ele.getUsername(), ele.getEmail(), ele.getDisplayName(),
                    roleRepository.findByUserId(ele.getId())));
        });
        ;
        return new BasicPageResponse<>(dto, users);
    }

}

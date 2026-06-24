package com.gsgd.generic_erp.controller.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.service.admin.UserManagementService;
import com.gsgd.generic_erp.util.BasicPageResponse;

@RestController
@RequestMapping("/api/root")
class UserManagementController {

    private UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping("/user/fetch")
    public BasicPageResponse<User, UserDTO> fetchUsers(Pageable pageable) {
        return service.fetchUserList(pageable);
    }

    @RequestMapping(path = "/user/detail", method = RequestMethod.GET)
    public String fetchUserDetail(@RequestParam String id) {
        return new String();
    }

}
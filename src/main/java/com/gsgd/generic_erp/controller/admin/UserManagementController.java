package com.gsgd.generic_erp.controller.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.service.admin.UserManagementService;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

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

    @RequestMapping(path = "/users")
    public SimpleResponse saveOrUpdate(@RequestBody UserDTO user) {
        return service.saveOrUpdate(user.getId(), user);
    }

    @RequestMapping(path = "/delete/{id}", method = RequestMethod.DELETE)
    public SimpleResponse deleteUser(@PathVariable Long id) {
        return service.deleteUser(id);
    }

}
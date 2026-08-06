package com.gsgd.generic_erp.controller.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.service.admin.UserManagementService;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Administrative user-management endpoints ({@code /api/root}):
 * paginated user listing, create/update, and deletion.
 */
@RestController
@RequestMapping("/api/root")
class UserManagementController {

    private UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    /** Returns a page of users mapped to {@link UserDTO}. */
    @GetMapping("/user/fetch")
    public BasicPageResponse<User, UserDTO> fetchUsers(Pageable pageable) {
        return service.fetchUserList(pageable);
    }

    /** Returns a single user's detail. TODO: not yet implemented. */
    @RequestMapping(path = "/user/detail", method = RequestMethod.GET)
    public String fetchUserDetail(@RequestParam String id) {
        return new String();
    }

    /**
     * Creates a new user, or updates an existing one when the DTO carries an id.
     */
    @RequestMapping(path = "/users")
    public SimpleResponse saveOrUpdate(@RequestBody UserDTO user) {
        return service.saveOrUpdate(user.getId(), user);
    }

    /**
     * Deletes a user by id.
     * 
     * @throws NoneDeleteableException
     */
    @RequestMapping(path = "/delete/{id}", method = RequestMethod.DELETE)
    public SimpleResponse deleteUser(@PathVariable Long id) throws NoneDeleteableException {
        return service.deleteUser(id);
    }

}
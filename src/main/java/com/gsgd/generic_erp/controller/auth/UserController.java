package com.gsgd.generic_erp.controller.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.service.auth.UserService;
import com.gsgd.generic_erp.util.BasicResponse;

/**
 * User-facing endpoints ({@code /api/users}) for data belonging to the
 * authenticated user, e.g. the sidebar navigation menu.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Returns all sidebar menu items visible to the given user. */
    @RequestMapping(path = "/fetch/sidebar/menu", method = RequestMethod.GET)
    public BasicResponse fetchSideMenu(@RequestParam Long id) {
        return service.fetchNavMenu(id);
    }
}

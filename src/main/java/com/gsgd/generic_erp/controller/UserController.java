package com.gsgd.generic_erp.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @RequestMapping("/test")
    public String testRequest() {
        return "Acessed protected endpoint successfully!";
    }
}

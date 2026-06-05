package com.gsgd.generic_erp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.util.BasicResponse;


@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    @Autowired
    private  AuthenticationManager authenticationManager;
    @PostMapping("/login")
    public BasicResponse postMethodName(@RequestBody AuthenticationRequest entity) {
        Authentication auth= authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(entity.username, entity.password));
        String token=JWTUtil.generateToken(auth.getName()) ;
        return new BasicResponse(200, "Login successful", token);
    }
    public record AuthenticationRequest(String username, String password) {
    }     
}


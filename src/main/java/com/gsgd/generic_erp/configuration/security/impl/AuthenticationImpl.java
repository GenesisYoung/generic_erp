package com.gsgd.generic_erp.configuration.security.impl;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.AuthenticationService;

@Service
public class AuthenticationImpl implements AuthenticationService {

    @Override
    public String generatePass(String password) {
        return new Argon2PasswordEncoder(64, 128, 2, 1 << 16, 4).encode(password);
    }
    
}

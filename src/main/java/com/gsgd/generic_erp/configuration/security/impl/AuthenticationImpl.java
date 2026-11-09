package com.gsgd.generic_erp.configuration.security.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.AuthenticationServiceInterface;

/**
 * Argon2-based implementation of {@link AuthenticationServiceInterface}, used
 * when creating users. It delegates to the shared {@code PasswordEncoder} bean
 * in {@code WebSecurityConfiurer}, so hashes produced here always use exactly
 * the parameters that login verification expects.
 */
@Service
public class AuthenticationImpl implements AuthenticationServiceInterface {

    private final PasswordEncoder passwordEncoder;

    public AuthenticationImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String generatePass(String password) {
        return passwordEncoder.encode(password);
    }

}

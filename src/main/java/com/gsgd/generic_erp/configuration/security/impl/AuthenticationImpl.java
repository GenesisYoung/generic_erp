package com.gsgd.generic_erp.configuration.security.impl;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.AuthenticationService;

/**
 * Argon2-based implementation of {@link AuthenticationService}, used when
 * creating users. The encoder parameters must stay consistent with the
 * {@code PasswordEncoder} bean in {@code WebSecurityConfiurer} so that hashes
 * produced here can be verified during login.
 */
@Service
public class AuthenticationImpl implements AuthenticationService {

    @Override
    public String generatePass(String password) {
        // Argon2(saltLength, hashLength, parallelism, memory (KB), iterations)
        return new Argon2PasswordEncoder(64, 50, 2, 1 << 16, 4).encode(password);
    }

}

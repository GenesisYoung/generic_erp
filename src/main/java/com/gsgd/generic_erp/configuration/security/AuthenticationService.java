package com.gsgd.generic_erp.configuration.security;

import org.springframework.stereotype.Service;

/**
 * Abstraction for password hashing so callers do not depend on a concrete
 * algorithm. Implemented by {@code AuthenticationImpl} using Argon2.
 */
@Service
public interface AuthenticationService {

    /** Hashes a plain-text password for storage. */
    public String generatePass(String password);
}

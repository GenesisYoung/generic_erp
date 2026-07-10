package com.gsgd.generic_erp.configuration.security;

import org.springframework.stereotype.Service;

@Service
public interface AuthenticationService {
    public String generatePass(String password);
}

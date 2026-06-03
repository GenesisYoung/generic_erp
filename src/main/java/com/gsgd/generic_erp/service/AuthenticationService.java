package com.gsgd.generic_erp.service;

import org.springframework.stereotype.Service;

@Service
public interface AuthenticationService {
    public String authenticate(String username, String password);
}

package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gsgd.generic_erp.controller.admin.RoleController;
import com.gsgd.generic_erp.dto.RoleDTO;
import com.gsgd.generic_erp.service.admin.RoleManagementService;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Runs a real controller through Spring's method-security proxy to prove the
 * {@code @perm} expressions resolve (bean name, parameter names) and deny.
 */
class MethodSecurityEnforcementTest {

    private AnnotationConfigApplicationContext context;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(RoleController.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void createNeedsCreatePermission() {
        authenticate("menu.roleManagement.update");
        assertThrows(AccessDeniedException.class, () -> controller.save(new RoleDTO()));

        authenticate("menu.roleManagement.create");
        assertEquals(200, controller.save(new RoleDTO()).getCode());
    }

    @Test
    void updateNeedsUpdatePermission() {
        RoleDTO existing = new RoleDTO(100L, "CLERK", 100);

        authenticate("menu.roleManagement.create");
        assertThrows(AccessDeniedException.class, () -> controller.save(existing));

        authenticate("menu.roleManagement");
        assertEquals(200, controller.save(existing).getCode());
    }

    @Test
    void unrelatedPermissionIsDenied() {
        authenticate("product");
        assertThrows(AccessDeniedException.class, () -> controller.delete(100L));
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        PermissionChecker perm() {
            return new PermissionChecker();
        }

        @Bean
        RoleManagementService roleManagementService() throws Exception {
            RoleManagementService service = mock(RoleManagementService.class);
            when(service.saveOrUpdate(any())).thenReturn(new SimpleResponse(200, ""));
            when(service.deleteRole(any())).thenReturn(new SimpleResponse(200, ""));
            return service;
        }

        @Bean
        RoleController roleController(RoleManagementService service) {
            return new RoleController(service);
        }
    }
}

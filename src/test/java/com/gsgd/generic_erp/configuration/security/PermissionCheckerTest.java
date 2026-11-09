package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionCheckerTest {

    private final PermissionChecker perm = new PermissionChecker();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void parentCodeGrantsChildren() {
        authenticate("menu.userManagement");

        assertTrue(perm.has("menu.userManagement.read"));
        assertTrue(perm.has("menu.userManagement.delete"));
        assertFalse(perm.has("menu.roleManagement.read"));
    }

    @Test
    void childCodeDoesNotGrantSiblingsOrParent() {
        authenticate("menu.userManagement.read");

        assertTrue(perm.has("menu.userManagement.read"));
        assertFalse(perm.has("menu.userManagement.update"));
        assertFalse(perm.has("menu.userManagement"));
    }

    @Test
    void prefixMustEndAtSegmentBoundary() {
        authenticate("menu.user");

        assertFalse(perm.has("menu.userManagement.read"));
    }

    @Test
    void rootPassesEveryCheckButOtherRolesDoNot() {
        authenticate("ROLE_ROOT");
        assertTrue(perm.has("anything.at.all"));

        authenticate("ROLE_ADMIN");
        assertFalse(perm.has("menu.userManagement.read"));
    }

    @Test
    void canSavePicksCreateOrUpdate() {
        authenticate("product.update");

        assertFalse(perm.canSave("product", null));
        assertFalse(perm.canSave("product", 0L));
        assertTrue(perm.canSave("product", 7L));
    }

    @Test
    void anonymousIsDenied() {
        assertFalse(perm.has("product.read"));
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}

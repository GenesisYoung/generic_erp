package com.gsgd.generic_erp.configuration.security;

import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central rules for the built-in {@code ROOT} / {@code ADMIN} roles.
 * <p>
 * Spring authorities are derived from {@code role_tb.role_name}
 * ({@code ROLE_<name>}), so whoever can rename a role, create a role with a
 * reserved name, or attach the ROOT role to a user effectively controls who is
 * ROOT. Only a ROOT caller may do anything that grants or touches ROOT.
 */
@Component
public class RoleGuard {

    public static final String ROOT_ROLE = "ROOT";
    public static final String ADMIN_ROLE = "ADMIN";
    /** The seeded root account; it can never be disabled or lose ROOT. */
    public static final long ROOT_USER_ID = 1L;

    private static final Set<String> BUILT_IN_ROLES = Set.of(ROOT_ROLE, ADMIN_ROLE);

    /** True when the authenticated caller holds the ROOT role. */
    public boolean currentUserIsRoot() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + ROOT_ROLE).equals(a.getAuthority()));
    }

    /**
     * True for ROOT/ADMIN, compared case-insensitively so look-alike names such
     * as "root" or " Admin " cannot be created either.
     */
    public boolean isBuiltInRoleName(String roleName) {
        return roleName != null && BUILT_IN_ROLES.contains(roleName.trim().toUpperCase(Locale.ROOT));
    }
}

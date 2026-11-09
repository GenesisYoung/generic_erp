package com.gsgd.generic_erp.configuration.security;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * API-level permission checks, used from {@code @PreAuthorize} as
 * {@code @perm.has('menu.userManagement.update')}.
 * <p>
 * Permission codes are hierarchical (see {@code PermissionService}): holding a
 * code grants every code beneath it, so a role holding
 * {@code menu.userManagement} may call anything requiring
 * {@code menu.userManagement.read}, {@code .create}, {@code .update} or
 * {@code .delete}, while a role holding only {@code menu.userManagement.read}
 * is read-only. ROOT passes every check, so newly added permissions never lock
 * the root account out.
 * <p>
 * Action suffixes in use: {@code read}, {@code create}, {@code update},
 * {@code delete} (plus {@code import} for products).
 */
@Component("perm")
public class PermissionChecker {

    /** True when the current caller holds {@code code} or one of its ancestors. */
    public boolean has(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || code == null || code.isBlank()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String granted = authority.getAuthority();
            if (granted == null) {
                continue;
            }
            if (("ROLE_" + RoleGuard.ROOT_ROLE).equals(granted)) {
                return true;
            }
            if (granted.startsWith("ROLE_")) {
                continue;
            }
            if (code.equals(granted) || code.startsWith(granted + ".")) {
                return true;
            }
        }
        return false;
    }

    /** True when the caller passes {@link #has} for at least one of the codes. */
    public boolean hasAny(String... codes) {
        for (String code : codes) {
            if (has(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the caller passes {@link #has} for every code. Used to stop a
     * caller from granting permissions (directly or via a role) that they do not
     * hold themselves.
     */
    public boolean hasAll(Collection<String> codes) {
        return codes.stream().allMatch(this::has);
    }

    /**
     * Picks {@code .create} for a new record (null/0 id) and {@code .update}
     * otherwise, for save endpoints that handle both.
     */
    public boolean canSave(String resource, Long id) {
        return has(resource + (id == null || id == 0 ? ".create" : ".update"));
    }
}

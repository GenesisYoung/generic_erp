package com.gsgd.generic_erp.service.admin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Revoke all links to deleted permissions in the caller's transaction. */
@Repository
public class PermissionAssociationCleanup {
    private final NamedParameterJdbcTemplate jdbc;

    public PermissionAssociationCleanup(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(Collection<Long> ids) {
        if (ids.isEmpty())
            return;
        // Fixed table names only. NULL permission_id baseline menu grants are
        // preserved.
        for (String table : List.of("page_permission_approvals_record", "menu_registered_permissions_record",
                "role_permission_tb", "user_nav_menu", "action_permission_tb")) {
            jdbc.update("DELETE FROM " + table + " WHERE permission_id IN (:ids)", Map.of("ids", ids));
        }
    }
}

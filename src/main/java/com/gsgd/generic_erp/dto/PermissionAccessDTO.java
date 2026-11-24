package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A permission row in the "which permissions a given user holds for this
 * menu" roster.
 * <ul>
 * <li>{@code granted}: what the user has now (department default with the
 * page override applied).</li>
 * <li>{@code defaultGranted}: what the user's department role gives.</li>
 * <li>{@code overridden}: true when this page sets the permission for the user
 * instead of the department default.</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionAccessDTO {
    private Long id;
    private String permissionCode;
    private boolean granted;
    private boolean defaultGranted;
    private boolean overridden;

    /** A row with no override: the granted state is the default. */
    public PermissionAccessDTO(Long id, String permissionCode, boolean granted) {
        this(id, permissionCode, granted, granted, false);
    }
}

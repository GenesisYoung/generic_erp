package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A permission row in the "which permissions are registered for this menu"
 * roster — governs whether a permission can appear (and be approved) under
 * a menu at all.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionRegistrationDTO {
    private Long id;
    private String permissionCode;
    private boolean registered;
}

package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A permission row in the "which permissions a given user holds for this
 * menu" roster.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionAccessDTO {
    private Long id;
    private String permissionName;
    private Long val;
    private boolean granted;
}

package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A role row in the "which roles a given department holds" roster:
 * the role's id and name, plus whether a {@code role_department_tb} link
 * already exists ("Processed" vs "Not granted").
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleGrantDTO {
    private Long id;
    private String roleName;
    private boolean granted;
}

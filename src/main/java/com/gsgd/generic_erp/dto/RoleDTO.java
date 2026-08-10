package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Role DTO used by the role-management list: the role's id, display name and
 * numeric value. The list renders {@code roleName} as the title and
 * {@code id} as the subtitle.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDTO {
    private Long id;
    private String roleName;
    private Integer val;
}

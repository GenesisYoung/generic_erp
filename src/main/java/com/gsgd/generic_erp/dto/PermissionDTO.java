package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Permission DTO exposing only the id and hierarchical permission code. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {
    private Long id;
    private String permissionCode;
}

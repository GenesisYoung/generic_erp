package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A user row in the "which users can reach this menu" roster.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAccessDTO {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private boolean granted;
}

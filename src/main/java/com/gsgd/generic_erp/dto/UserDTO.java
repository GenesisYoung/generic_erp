package com.gsgd.generic_erp.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User DTO for listing and save/update requests. Carries role values (not ids)
 * and account flags; never the password.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String displayName;
    private List<Integer> roles;
    private Boolean active;
    private Byte isEnabled;
}

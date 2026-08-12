package com.gsgd.generic_erp.dto;

import java.util.List;

import com.gsgd.generic_erp.view.UserRoleView;

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

    // Basic data
    private Long id;
    private String name;
    private String email;
    private String displayName;
    private List<UserRoleView> roles;
    private Boolean active;
    private Byte isEnabled;
    private List<Integer> roleList;
    // User info data
    private String realName;
    private String title;
    private String birthday;
    private String hireDate;
    // User's department data
    private List<Long> departments;
}

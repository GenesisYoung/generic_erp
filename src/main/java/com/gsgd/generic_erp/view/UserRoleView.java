package com.gsgd.generic_erp.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleView {
    private Long userId;

    private Long roleId;

    private Integer value;

    private String title;
}

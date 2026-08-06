package com.gsgd.generic_erp.view;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRoleView {
    private Long userId;

    private Long roleId;

    private Integer value;

    private String title;
}

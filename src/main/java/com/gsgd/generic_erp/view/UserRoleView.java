package com.gsgd.generic_erp.view;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleView {
    private Long userId;

    private Long roleId;

    private Integer value;

    private String title;

}

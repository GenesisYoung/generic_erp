package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserNavMenuDTO {
    private Long id;
    private String titleKey;
    private String icon;
    private String route;
    private String color;
}

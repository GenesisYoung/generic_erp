package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuDTO {
    private Long id;
    private String titleKey;
    private String icon;
    private String route;
    private String color;
}

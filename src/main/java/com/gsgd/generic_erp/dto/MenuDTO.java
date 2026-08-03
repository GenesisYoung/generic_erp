package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Navigation menu item DTO; also doubles as the filter object for menu queries. */
@Data
@AllArgsConstructor
public class MenuDTO {
    private Long id;
    private Long parentId;
    private String titleKey;
    private String icon;
    private String route;
    private String color;
}

package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sidebar menu item as delivered to the frontend (i18n title key, icon, route). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserNavMenuDTO {
    private Long id;
    private Long parentId;
    private String titleKey;
    private String icon;
    private String route;
    private String color;
}

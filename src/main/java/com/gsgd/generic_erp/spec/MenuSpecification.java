package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.gsgd.generic_erp.dto.MenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;

public class MenuSpecification {

    public static Specification<NavigationMenu> titleKeyContains(String titleKey) {
        return !StringUtils.hasText(titleKey)
                ? (root, query, builder) -> builder.conjunction()
                : (root, query, builder) -> builder.like(root.get("titleKey"), "%" + titleKey.trim() + "%");
    }

    public static Specification<NavigationMenu> routeContains(String route) {
        return !StringUtils.hasText(route)
                ? (root, query, builder) -> builder.conjunction()
                : (root, query, builder) -> builder.like(root.get("route"), "%" + route.trim() + "%");
    }

    public static Specification<NavigationMenu> valid() {
        return (root, query, builder) -> builder.and(root.isNotNull(), root.get("route").notEqualTo(""));
    }

    public static Specification<NavigationMenu> filter(MenuDTO f) {
        return Specification.allOf( // Spring Data JPA 3.x
                titleKeyContains(f.getTitleKey()),
                routeContains(f.getRoute()));
    }

    public static Specification<NavigationMenu> filterValid(MenuDTO f) {
        return Specification.allOf( // Spring Data JPA 3.x
                valid(),
                titleKeyContains(f.getTitleKey()),
                routeContains(f.getRoute()));
    }
}

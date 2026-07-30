package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.view.NavigationPermissionView;

public class NavigationMenuSpecification {
    private static Specification<NavigationPermissionView> filterByUserName(String uName) {
        return !StringUtils.hasText(uName) ? (root, query, builder) -> builder.conjunction()
                : (root, query, builder) -> builder.like(root.get("uName"), uName);
    }

    private static Specification<NavigationPermissionView> filterBynavName(String navName) {
        return !StringUtils.hasText(
                navName) ? (root, query, builder) -> builder.conjunction()
                        : (root, query, builder) -> builder.like(root.get("navName"), navName);
    }

    private static Specification<NavigationPermissionView> filterBypName(String pName) {
        return !StringUtils.hasText(
                pName) ? (root, query, builder) -> builder.conjunction()
                        : (root, query, builder) -> builder.like(root.get("pName"), pName);
    }

    public static Specification<NavigationPermissionView> findByFilter(PermissionMenuViewFilter filter) {
        return Specification.allOf(filterByUserName(filter.uName()), filterBynavName(filter.navName()),
                filterBypName(filter.pName()));
    }
}

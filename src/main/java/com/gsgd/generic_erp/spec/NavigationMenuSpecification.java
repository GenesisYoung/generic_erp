package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.gsgd.generic_erp.dto.filter.PermissionMenuViewFilter;
import com.gsgd.generic_erp.view.NavigationPermissionView;

/**
 * JPA Specifications for filtering the {@link NavigationPermissionView} by
 * username, menu key, permission name, and route. Blank values are ignored.
 */
public class NavigationMenuSpecification {
    private static Specification<NavigationPermissionView> filterByUserName(String uName) {
        return !StringUtils.hasText(uName) ? (root, query, builder) -> builder.conjunction()
                : (root, query, builder) -> builder.like(root.get("uName"), uName);
    }

    private static Specification<NavigationPermissionView> filterBynavName(String navName) {
        return !StringUtils.hasText(
                navName) ? (root, query, builder) -> builder.conjunction()
                        : (root, query, builder) -> builder.like(root.get("navKey"), navName);
    }

    private static Specification<NavigationPermissionView> filterBypName(String pName) {
        return !StringUtils.hasText(
                pName) ? (root, query, builder) -> builder.conjunction()
                        : (root, query, builder) -> builder.like(root.get("pName"), pName);
    }

    private static Specification<NavigationPermissionView> filterByRoute(String route) {
        return !StringUtils.hasText(
                route) ? (root, query, builder) -> builder.conjunction()
                        : (root, query, builder) -> builder.like(root.get("route"), route);
    }

    public static Specification<NavigationPermissionView> findByFilter(PermissionMenuViewFilter filter) {
        return Specification.allOf(filterByUserName(filter.uName()), filterBynavName(filter.navName()),
                filterBypName(filter.pName()), filterByRoute(filter.route()));
    }
}

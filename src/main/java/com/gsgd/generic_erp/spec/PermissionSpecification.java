package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.auth.Permission;

/**
 * JPA Specifications for {@link Permission} queries (name search, batch
 * delete).
 */
public class PermissionSpecification {
    public static Specification<Permission> hasPermissionName(String permissionName) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(criteriaBuilder.like(root.get("permissionCode"),
                "%" + permissionName + "%"), criteriaBuilder.equal(root.get("isEnabled"), 1));
    }

    public static Specification<Permission> enabled() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("isEnabled"), 1);
    }

    // public static PredicateSpecification<Permission> deleteByVal(Long[] vals) {
    // return (root, cb) -> {
    // return root.get("val").in((Object[]) vals);
    // };
    // }

    public static Specification<Permission> findMenuPermission() {
        return (root, query, builder) -> builder.and(builder.like(root.get("permissionCode"), "menu.%"), builder.equal(
                root.get("isEnabled"), 1));
    }
}

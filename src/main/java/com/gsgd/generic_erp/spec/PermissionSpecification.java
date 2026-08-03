package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.auth.Permission;

/** JPA Specifications for {@link Permission} queries (name search, batch delete). */
public class PermissionSpecification {
    public static Specification<Permission> hasPermissionName(String permissionName) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("permissionName"),
                "%" + permissionName + "%");
    }

    public static PredicateSpecification<Permission> deleteByVal(Long[] vals) {
        return (root, cb) -> {
            return root.get("val").in((Object[]) vals);
        };
    }
}

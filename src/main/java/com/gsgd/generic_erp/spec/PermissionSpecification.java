package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.auth.Permission;

public class PermissionSpecification {
    public static Specification<Permission> hasPermissionName(String permissionName) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("permissionName"),
                "%" + permissionName + "%");
    }
}

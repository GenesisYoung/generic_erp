package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.auth.User;

public class UserSpecification {
    public static Specification<User> excludeDisabled(Byte isEnabled) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("isEnabled"), isEnabled);
    }
}

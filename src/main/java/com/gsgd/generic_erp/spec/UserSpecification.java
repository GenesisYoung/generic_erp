package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.auth.User;

/** JPA Specifications for {@link User} queries. */
public class UserSpecification {

    /** Matches users whose {@code isEnabled} flag equals the given value (1 = enabled). */
    public static Specification<User> excludeDisabled(Byte isEnabled) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("isEnabled"), isEnabled);
    }
}

package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.entity.product.Category;

public class ProductCategorySpecification {
    public static Specification<Category> enabled() {
        return (root, query, builder) -> builder.equal(root.get("active"), true);
    }
}

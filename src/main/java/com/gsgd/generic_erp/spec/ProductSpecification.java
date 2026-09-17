package com.gsgd.generic_erp.spec;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.dto.filter.PlainProductFilter;
import com.gsgd.generic_erp.entity.product.Product;
import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;

public class ProductSpecification {
    private static Specification<Product> byId(Long id) {
        if (id == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("productId"), id);
    }

    private static Specification<Product> byCode(String code) {
        if (code == null || code.isEmpty())
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.like(root.get("productCode"), "%" + code + "%");
    }

    private static Specification<Product> byName(String name) {
        if (name == null || name.isEmpty())
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.like(root.get("productName"), "%" + name + "%");
    }

    private static Specification<Product> byType(ProductType type) {
        if (type == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.like(root.get("type"), "%" + type + "%");
    }

    private static Specification<Product> isConfigurable(Boolean configurable) {
        if (configurable == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("configurable"), configurable);
    }

    private static Specification<Product> isStatus(ProductStatus status) {
        if (status == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Product> filter(PlainProductFilter filter) {
        return (root, query, builder) -> builder.and(
                byId(filter.productId()).toPredicate(root, query, builder),
                byCode(filter.productCode()).toPredicate(root, query, builder),
                byName(filter.productname()).toPredicate(root, query, builder),
                byType(filter.type()).toPredicate(root, query, builder),
                isConfigurable(filter.configurable()).toPredicate(root, query, builder),
                isStatus(filter.status()).toPredicate(root, query, builder),
                builder.equal(root.get("isEnabled"), true));
    }
}

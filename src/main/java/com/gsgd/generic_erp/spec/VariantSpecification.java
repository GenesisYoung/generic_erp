package com.gsgd.generic_erp.spec;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.dto.filter.ProductVariantFilter;
import com.gsgd.generic_erp.entity.product.ProductVariant;

public class VariantSpecification {
    public static Specification<ProductVariant> filter(ProductVariantFilter filter) {
        return (root, query, builder) -> builder.and(
                byDefaultVaraint(filter.isDefault()).toPredicate(root, query, builder),
                byVariantId(filter.variantId()).toPredicate(root, query,
                        builder),
                byProductId(filter.productId()).toPredicate(root, query,
                        builder),
                bySku(filter.sku()).toPredicate(root, query,
                        builder),
                byVariantName(filter.variantName()).toPredicate(root, query,
                        builder),
                byMaxPrice(filter.max()).toPredicate(root, query,
                        builder),
                byMinPrice(filter.min()).toPredicate(root, query, builder));
    }

    public static Specification<ProductVariant> byVariantId(Long variantId) {
        if (variantId == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("variantId"), variantId);
    }

    public static Specification<ProductVariant> byProductId(Long productId) {
        if (productId == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("productId"), productId);
    }

    public static Specification<ProductVariant> bySku(String sku) {
        if (sku == null || sku.isEmpty())
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.like(root.get("sku"), "%" + sku + "%");
    }

    public static Specification<ProductVariant> byVariantName(String variantName) {
        if (variantName == null || variantName.isEmpty())
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.like(root.get("variantName"), "%" + variantName + "%");
    }

    public static Specification<ProductVariant> byDefaultVaraint(Boolean isDefault) {
        if (isDefault == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.equal(root.get("defaultVariant"), isDefault);
    }

    public static Specification<ProductVariant> byMaxPrice(BigDecimal max) {
        if (max == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("listPrice"), max);
    }

    public static Specification<ProductVariant> byMinPrice(BigDecimal min) {
        if (min == null)
            return (root, query, builder) -> builder.conjunction();
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("listPrice"), min);
    }
}

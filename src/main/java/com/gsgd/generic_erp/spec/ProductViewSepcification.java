package com.gsgd.generic_erp.spec;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

import com.gsgd.generic_erp.dto.filter.ProductFilter;
import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;
import com.gsgd.generic_erp.view.sql.ProductVariantCategoryView;

public class ProductViewSepcification {
    public static Specification<ProductVariantCategoryView> hasProductCode(String productCode) {
        if (productCode == null || productCode.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("productCode"),
                    "%" + productCode + "%");
        }
    }

    public static Specification<ProductVariantCategoryView> hasProductName(String productName) {
        if (productName == null || productName.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("productName"),
                    "%" + productName + "%");
        }
    }

    public static Specification<ProductVariantCategoryView> isType(String type) {
        if (type == null || type.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), ProductType.valueOf(type));
        }
    }

    public static Specification<ProductVariantCategoryView> configurable(Boolean configurable) {
        if (configurable == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("configurable"), configurable);
        }
    }

    public static Specification<ProductVariantCategoryView> isStatus(String status) {
        if (status == null || status.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"),
                    ProductStatus.valueOf(status));
        }
    }

    public static Specification<ProductVariantCategoryView> hasSku(String sku) {
        if (sku == null || sku.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("sku"), "%" + sku + "%");
        }
    }

    public static Specification<ProductVariantCategoryView> isCategory(Long categoryId) {
        if (categoryId == null || categoryId == -1) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("cateId"), categoryId);
        }
    }

    public static Specification<ProductVariantCategoryView> maxPrice(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("listPrice"), maxPrice);
        }
    }

    public static Specification<ProductVariantCategoryView> minPrice(BigDecimal minPrice) {
        if (minPrice == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("listPrice"),
                    minPrice);
        }
    }

    public static Specification<ProductVariantCategoryView> hasTaxCategory(String taxCategory) {
        if (taxCategory == null || taxCategory.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        } else {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("taxCategory"), taxCategory);
        }
    }

    public static Specification<ProductVariantCategoryView> filter(
            ProductFilter filter) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                hasProductCode(filter.productCode()).toPredicate(root, query, criteriaBuilder),
                hasProductName(filter.productName()).toPredicate(root, query, criteriaBuilder),
                isType(filter.type()).toPredicate(root, query, criteriaBuilder),
                isStatus(filter.status()).toPredicate(root, query, criteriaBuilder),
                hasSku(filter.sku()).toPredicate(root, query, criteriaBuilder),
                isCategory(filter.cateId()).toPredicate(root, query, criteriaBuilder),
                maxPrice(filter.maxPrice()).toPredicate(root, query, criteriaBuilder),
                minPrice(filter.minPrice()).toPredicate(root, query, criteriaBuilder),
                configurable(filter.configurable()).toPredicate(root, query, criteriaBuilder),
                hasTaxCategory(filter.taxCategory()).toPredicate(root, query, criteriaBuilder));
    }
}

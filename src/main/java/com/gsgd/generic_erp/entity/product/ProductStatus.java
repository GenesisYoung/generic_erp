package com.gsgd.generic_erp.entity.product;

/** Lifecycle status of a product. */
public enum ProductStatus {
    /** Product data is not yet ready for operational use. */
    DRAFT,
    /** Product is available to operational modules. */
    ACTIVE,
    /** Product is retained for history but unavailable for new business. */
    DISCONTINUED
}

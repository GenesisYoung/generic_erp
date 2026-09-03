package com.gsgd.generic_erp.entity.product;

/** Operational use of a product-specific alternative unit. */
public enum ProductUomUsageType {
    /** Available only for purchasing. */
    PURCHASE,
    /** Available only for sales. */
    SALE,
    /** Available for both purchasing and sales. */
    BOTH
}

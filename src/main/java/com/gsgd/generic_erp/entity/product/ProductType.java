package com.gsgd.generic_erp.entity.product;

/** Business type of a product. */
public enum ProductType {
    /** Physical item whose inventory quantity is tracked. */
    STOCKABLE,
    /** Service that occupies no warehouse space and has no inventory quantity. */
    SERVICE,
    /** Physical supply consumed internally or as part of a service. */
    CONSUMABLE
}

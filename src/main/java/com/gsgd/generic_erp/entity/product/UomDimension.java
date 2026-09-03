package com.gsgd.generic_erp.entity.product;

/** Physical dimension to which a unit of measure belongs. */
public enum UomDimension {
    /** Mass. */
    MASS,
    /** Length. */
    LENGTH,
    /** Volume. */
    VOLUME,
    /** Area. */
    AREA,
    /** Time. */
    TIME,
    /** Temperature; conversion cannot be represented by a multiplier alone. */
    TEMP,
    /** Density. */
    DENS,
    /** Velocity. */
    VELOC,
    /** Acceleration. */
    ACCEL,
    /** Energy. */
    ENERGY,
    /** Power. */
    POWER,
    /** Pressure. */
    PRESS,
    /** Count units such as each, box, and pallet; conversion is product-specific. */
    COUNT
}

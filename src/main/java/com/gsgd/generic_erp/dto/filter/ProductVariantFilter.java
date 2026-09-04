package com.gsgd.generic_erp.dto.filter;

import java.math.BigDecimal;

public record ProductVariantFilter(Long variantId, Long productId, String sku, String variantName, Boolean isDefault,
        BigDecimal max, BigDecimal min) {
}
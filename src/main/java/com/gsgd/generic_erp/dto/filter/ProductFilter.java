package com.gsgd.generic_erp.dto.filter;

import java.math.BigDecimal;

public record ProductFilter(String productCode, String productName, String sku, Long cateId, String taxCategory,
                String type, String status, BigDecimal listPrice, BigDecimal minPrice, BigDecimal maxPrice,
                Boolean configurable) {
}
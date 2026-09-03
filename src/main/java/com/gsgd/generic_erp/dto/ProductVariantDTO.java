package com.gsgd.generic_erp.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantDTO {
    private Long variantId;
    private Long productId;
    private String sku;
    private String variantName;
    private Boolean defaultVariant;
    private String description;
    private BigDecimal standardCost;
    private BigDecimal listPrice;
    private String currency;
    private BigDecimal netWeight;
    private String weightUom;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private String sizeUom;
}

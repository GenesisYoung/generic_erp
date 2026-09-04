package com.gsgd.generic_erp.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class VariantsDTO {
    private Long variantId;
    private Long productId;
    private String sku;
    private String variantname;
    private Boolean defaultVariant;
    private String description;
    private BigDecimal standardCost;
    private BigDecimal listPrice;
    private String currency;
    private BigDecimal netWeight;
    private Long weightUomId;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private Long sizeUomId;
    private Boolean active;
}

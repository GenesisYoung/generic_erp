package com.gsgd.generic_erp.dto;

import java.math.BigDecimal;

import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDTO {
    private Long productId;
    private String productCode;
    private String productName;
    private String productDescription;
    private ProductType type;
    private Boolean configurable;
    private ProductStatus status;
    private Long baseUomId;
    private Boolean isEnabled;
    private String taxCategory;
    // Default variant(only when configurable is false will create a default
    // variant)
    private String sku;
    private String variantName;
    @Builder.Default
    private Boolean defaultVariant = true;
    private BigDecimal standardCost;
    private BigDecimal listPrice;
    private String currency;
    private BigDecimal netWeight;
    private Long weightUomId;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private Long sizeUomId;
}

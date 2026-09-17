package com.gsgd.generic_erp.dto.filter;

import org.springframework.data.domain.Sort;

import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;

public record PlainProductFilter(Long productId, String productCode, String productname, ProductType type,
                Boolean configurable,
                ProductStatus status, String orderBy, Sort.Direction order) {
        public PlainProductFilter {
                if (orderBy == null || orderBy.isEmpty())
                        orderBy = "productId";
                if (order == null)
                        order = Sort.Direction.ASC;
                if (status == null)
                        status = ProductStatus.ACTIVE;
        }
}

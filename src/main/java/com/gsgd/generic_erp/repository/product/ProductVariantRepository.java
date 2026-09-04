package com.gsgd.generic_erp.repository.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.product.ProductVariant;

public interface ProductVariantRepository
                extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

}

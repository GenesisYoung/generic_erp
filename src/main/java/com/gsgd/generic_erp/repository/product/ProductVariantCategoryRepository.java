package com.gsgd.generic_erp.repository.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.view.sql.ProductVariantCategoryView;

public interface ProductVariantCategoryRepository
        extends JpaRepository<ProductVariantCategoryView, String>,
        JpaSpecificationExecutor<ProductVariantCategoryView> {

}

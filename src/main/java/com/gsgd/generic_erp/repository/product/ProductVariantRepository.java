package com.gsgd.generic_erp.repository.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.gsgd.generic_erp.entity.product.ProductVariant;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

    @Modifying
    @Query("UPDATE ProductVariant v SET v.active=false WHERE v.variantId=?1")
    void disableVariant(Long id);

    List<ProductVariant> findByProductId(Long id);

}

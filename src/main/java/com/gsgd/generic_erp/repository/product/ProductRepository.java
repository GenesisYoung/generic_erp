package com.gsgd.generic_erp.repository.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.gsgd.generic_erp.entity.product.Product;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Modifying
    @Query("UPDATE Product p SET p.isEnabled=false WHERE p.productId=?1")
    void disableProduct(Long id);
}

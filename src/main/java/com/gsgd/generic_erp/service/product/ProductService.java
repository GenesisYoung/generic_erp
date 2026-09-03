package com.gsgd.generic_erp.service.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.ProductFilter;
import com.gsgd.generic_erp.repository.product.ProductVariantCategoryRepository;
import com.gsgd.generic_erp.spec.ProductViewSepcification;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.SimplePageResponse;
import com.gsgd.generic_erp.view.sql.ProductVariantCategoryView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    // private final ProductRepository pRepository;
    private final ProductVariantCategoryRepository viewRepository;

    public SimplePageResponse fetchProducts(boolean isMaxprice, ProductFilter filter, BasicPage page) {
        Page<ProductVariantCategoryView> result = viewRepository.findAll(ProductViewSepcification.filter(isMaxprice,
                filter),
                PageRequest.of(page.getPageNumber(), page.getPageSize(), page.getSort()));
        return SimplePageResponse.builder()
                .content(result.getContent())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

}

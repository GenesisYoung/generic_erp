package com.gsgd.generic_erp.controller.product;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.ProductFilter;
import com.gsgd.generic_erp.service.product.ProductService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.SimplePageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @RequestMapping("/fetch")
    public SimplePageResponse fetchProducts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean isMaxprice,
            @ModelAttribute ProductFilter filter) {
        BasicPage p = new BasicPage(page, size);
        p.defineSort(Sort.by("productId").ascending());
        return service.fetchProducts(isMaxprice, filter, p);
    }
}

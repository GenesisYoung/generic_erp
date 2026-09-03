package com.gsgd.generic_erp.controller.product;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.ProductDTO;
import com.gsgd.generic_erp.dto.ProductFilter;
import com.gsgd.generic_erp.service.product.ProductService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.SimplePageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @RequestMapping("/fetch")
    public SimplePageResponse fetchProducts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean isMinPrice,
            @RequestParam(defaultValue = "false") boolean isMaxprice,
            @RequestParam(required = false, defaultValue = "productId") String sortProperty,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction sortDirection,
            @ModelAttribute ProductFilter filter) {
        BasicPage p = new BasicPage(page, size);
        if (sortDirection.equals(Sort.Direction.ASC))
            p.defineSort(Sort.by(sortProperty).ascending());
        else
            p.defineSort(Sort.by(sortProperty).descending());
        return service.fetchProducts(isMinPrice, isMaxprice, filter, p);
    }

    @RequestMapping(path = "/detail/{id}")
    public SimplePageResponse productDetail(@PathVariable Long id) {
        return service.findByProductId(id);
    }

    @PostMapping(path = "/save")
    public SimpleResponse saveProduct(@RequestBody ProductDTO product) {
        return service.saveProduct(product);
    }

}

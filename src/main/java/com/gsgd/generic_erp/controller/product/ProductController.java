package com.gsgd.generic_erp.controller.product;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gsgd.generic_erp.dto.ProductDTO;
import com.gsgd.generic_erp.dto.ProductVariantDTO;
import com.gsgd.generic_erp.dto.filter.PlainProductFilter;
import com.gsgd.generic_erp.dto.filter.ProductFilter;
import com.gsgd.generic_erp.dto.filter.ProductVariantFilter;
import com.gsgd.generic_erp.service.product.ProductService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimplePageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import io.jsonwebtoken.lang.Arrays;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @PreAuthorize("@perm.has('product.read')")
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
        return service.fetchProducts(filter, p);
    }

    @PreAuthorize("@perm.has('product.read')")
    @RequestMapping(path = "/detail/{id}")
    public SimplePageResponse productDetail(@PathVariable Long id) {
        return service.findByProductId(id);
    }

    @PreAuthorize("@perm.canSave('product', #product.productId)")
    @PostMapping(path = "/save")
    public SimpleResponse saveProduct(@RequestBody ProductDTO product) {
        return service.saveProduct(product);
    }

    @PreAuthorize("@perm.has('product.delete')")
    @RequestMapping(path = "/disable", method = RequestMethod.GET)
    public SimpleResponse requestMethodName(@RequestParam Long[] ids) {
        return service.disableProduct(ids);
    }

    @PreAuthorize("@perm.has('product.read')")
    @RequestMapping(path = "/fetch/variants")
    public SimplePageResponse fetchVariants(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "variantId") String sortProperty,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction sortDirection,
            @ModelAttribute ProductVariantFilter filter) {
        return service.queryVariants(filter, new BasicPage(page, size));
    }

    @PreAuthorize("@perm.has('product.read')")
    @RequestMapping(path = "/fetch/variant/detail/{id}")
    public BasicResponse variantDetail(@PathVariable Long id) {
        return service.findVariantById(id);
    }

    @PreAuthorize("@perm.has('product.update')")
    @PostMapping("/variant/save")
    public SimpleResponse saveVariant(@RequestBody ProductVariantDTO dto) {
        return service.saveVariant(dto);
    }

    @PreAuthorize("@perm.has('product.update')")
    @RequestMapping(path = "/variant/disable")
    public SimpleResponse disableVariant(@RequestParam Long[] ids) {
        return service.disableVariant(Arrays.asList(ids));
    }

    @PreAuthorize("@perm.has('product.read')")
    @RequestMapping(path = "/plain/product")
    public SimplePageResponse fetchPlainProduct(@ModelAttribute PlainProductFilter filter, @RequestParam int page,
            @RequestParam int size) {
        return service.fetchPlainProduct(filter,
                PageRequest.of(page, size));
    }

    @PreAuthorize("@perm.has('product.read')")
    @RequestMapping(path = "/plain/product/{id}")
    public BasicResponse fetchProductById(@PathVariable Long id) {
        return service.fetchPlainProductById(id);
    }

    @PreAuthorize("@perm.canSave('product', #dto.productId)")
    @RequestMapping(path = "/plain/product/saveOrUpdate")
    public SimpleResponse savePlainProduct(@RequestBody ProductDTO dto) {
        return service.saveOrUpdatePlainProduct(dto);
    }

    @PreAuthorize("@perm.has('product.delete')")
    @RequestMapping(path = "/plain/product/disable")
    public SimpleResponse requestDisable(@RequestParam Long[] ids) {
        return service.disableProduct(ids);
    }

    @PreAuthorize("@perm.has('product.import')")
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SimpleResponse importProductsByCSV(
            @RequestPart("file") MultipartFile file) {

        String filename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(filename);

        if (file.isEmpty() || !"csv".equalsIgnoreCase(extension)) {
            throw new IllegalArgumentException("A non-empty CSV file is required");
        }

        return service.importProducts(file);
    }

    @PreAuthorize("@perm.has('product.read')")
    @GetMapping("/export")
    public String exportAsCSV(@RequestParam int page, @RequestParam int size,
            @RequestParam(required = false, defaultValue = "productId") String sortProperty,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction sortDirection,
            @ModelAttribute ProductFilter filter, HttpServletResponse resp) {
        BasicPage p = new BasicPage(page, size);
        if (sortDirection.equals(Sort.Direction.ASC))
            p.defineSort(Sort.by(sortProperty).ascending());
        else
            p.defineSort(Sort.by(sortProperty).descending());
        return service.export(p, filter, resp);
    }

}

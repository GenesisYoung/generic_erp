package com.gsgd.generic_erp.service.product;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.gsgd.generic_erp.annotations.AuditAction;
import com.gsgd.generic_erp.annotations.AuditRecord;
import com.gsgd.generic_erp.dto.ProductDTO;
import com.gsgd.generic_erp.dto.ProductVariantDTO;
import com.gsgd.generic_erp.dto.filter.PlainProductFilter;
import com.gsgd.generic_erp.dto.filter.ProductFilter;
import com.gsgd.generic_erp.dto.filter.ProductVariantFilter;
import com.gsgd.generic_erp.entity.product.Product;
import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;
import com.gsgd.generic_erp.entity.product.ProductVariant;
import com.gsgd.generic_erp.repository.product.ProductRepository;
import com.gsgd.generic_erp.repository.product.ProductVariantCategoryRepository;
import com.gsgd.generic_erp.repository.product.ProductVariantRepository;
import com.gsgd.generic_erp.spec.ProductSpecification;
import com.gsgd.generic_erp.spec.ProductViewSepcification;
import com.gsgd.generic_erp.spec.VariantSpecification;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalUtil;
import com.gsgd.generic_erp.util.GlobalVariable;
import com.gsgd.generic_erp.util.SimplePageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;
import com.gsgd.generic_erp.view.sql.ProductVariantCategoryView;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

        private final ProductRepository pRepository;
        private final ProductVariantCategoryRepository viewRepository;
        private final ProductVariantRepository variantRepository;
        private final GlobalVariable globalVariable;
        private final Logger log = LoggerFactory.getLogger(ProductService.class);

        @Cacheable(value = "productQuery", keyGenerator = "jsonKeyGenerator")
        public SimplePageResponse fetchProducts(ProductFilter filter,
                        BasicPage page) {
                Page<ProductVariantCategoryView> result = viewRepository.findAll(
                                ProductViewSepcification.filter(filter),
                                PageRequest.of(page.getPageNumber(), page.getPageSize(), page.getSort()));
                return SimplePageResponse.builder()
                                .content(result.getContent())
                                .pageNumber(result.getNumber())
                                .pageSize(result.getSize())
                                .totalElements(result.getTotalElements())
                                .totalPages(result.getTotalPages())
                                .build();
        }

        @Cacheable(value = "productQuery", keyGenerator = "jsonKeyGenerator")
        public SimplePageResponse findByProductId(Long id) {
                return SimplePageResponse.builder()
                                .content(viewRepository.findByProductId(id))
                                .pageNumber(0)
                                .pageSize(1)
                                .totalElements(1)
                                .totalPages(1)
                                .build();
        }

        @Transactional
        @AuditRecord(action = AuditAction.CREATEORUPDATE, module = "PRODUCT", generateKey = "jsonKeyGenerator", description = "Create or update a product")
        @CacheEvict(cacheNames = "productQuery", allEntries = true)
        public SimpleResponse saveProduct(ProductDTO dto) {
                if (dto.getProductId() == null) {
                        pRepository.save(toEntity(dto));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("CREATE::")
                                        .build();
                } else {
                        Product p = pRepository.findById(dto.getProductId()).get();
                        pRepository.saveAndFlush(toEntityWithVersion(dto, p.getVersion()));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("UPDATE::")
                                        .build();
                }
        }

        @Cacheable(value = "productVariantQuery", keyGenerator = "jsonKeyGenerator")
        public SimplePageResponse queryVariants(ProductVariantFilter filter, BasicPage page) {
                Page<ProductVariant> variants = variantRepository.findAll(VariantSpecification.filter(
                                filter),
                                PageRequest.of(page.getPageNumber(), page.getPageSize()));
                return new SimplePageResponse(page.getPageNumber(), page.getPageSize(), variants.getTotalElements(),
                                variants.getTotalPages(), variants.getContent());
        }

        @Cacheable(value = "productVariantQuery", keyGenerator = "jsonKeyGenerator")
        public BasicResponse findVariantById(Long id) {
                Optional<ProductVariant> variant = variantRepository.findById(id);
                return BasicResponse.builder()
                                .status(200)
                                .object(variant.get())
                                .message("Success")
                                .build();

        }

        @Transactional
        @AuditRecord(action = AuditAction.CREATEORUPDATE, module = "PRODUCT", description = "Create or update a variant", generateKey = "jsonKeyGenerator")
        @CacheEvict(cacheNames = "productVariantQuery", allEntries = true)
        public SimpleResponse saveVariant(ProductVariantDTO dto) {
                if (dto.getVariantId() == null) {
                        variantRepository.save(toEntity(dto));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("CREATE::")
                                        .build();
                } else {
                        ProductVariant variant = variantRepository.findById(dto.getVariantId()).get();
                        variantRepository.saveAndFlush(toEntityWithVersion(dto, variant.getVersion()));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("UPDATE::")
                                        .build();
                }
        }

        @Transactional
        @AuditRecord(action = AuditAction.DELETE, module = "PRODUCT", generateKey = "jsonKeyGenerator")
        @CacheEvict(cacheNames = "productVariantQuery", allEntries = true)
        public SimpleResponse disableVariant(Iterable<Long> ids) {
                for (Long id : ids) {
                        variantRepository.disableVariant(id);
                }
                return SimpleResponse.builder()
                                .code(200)
                                .message("DIEABLED::")
                                .build();
        }

        @Transactional
        @AuditRecord(action = AuditAction.DELETE, module = "PRODUCT", generateKey = "jsonKeyGenerator")
        @CacheEvict(cacheNames = "productQuery,plainProduct", allEntries = true)
        public SimpleResponse disableProduct(Long[] ids) {
                for (Long id : ids) {
                        pRepository.disableProduct(id);
                        List<Long> variantIds = variantRepository.findByProductId(id).stream()
                                        .map(variant -> variant.getVariantId()).toList();
                        disableVariant(variantIds);

                }
                return SimpleResponse.builder()
                                .code(200)
                                .message("DIEABLED::")
                                .build();
        }

        @Cacheable(value = "plainProduct", keyGenerator = "jsonKeyGenerator")
        public SimplePageResponse fetchPlainProduct(PlainProductFilter filter, PageRequest of) {
                Page<Product> products = pRepository.findAll(ProductSpecification.filter(filter), of);
                return SimplePageResponse.builder()
                                .content(products.getContent())
                                .pageNumber(products.getNumber())
                                .pageSize(products.getSize())
                                .totalPages(products.getTotalPages())
                                .totalElements(products.getTotalElements())
                                .build();
        }

        @Cacheable(value = "plainProduct", keyGenerator = "jsonKeyGenerator")
        public BasicResponse fetchPlainProductById(Long id) {
                Optional<Product> p = pRepository.findById(id);
                return BasicResponse.builder()
                                .object(p.isPresent() ? p.get() : null)
                                .status(200)
                                .message("Success")
                                .build();
        }

        @Transactional
        @AuditRecord(action = AuditAction.CREATEORUPDATE, module = "PRODUCT")
        @CacheEvict(cacheNames = "plainProduct,productQuery", allEntries = true)
        public SimpleResponse saveOrUpdatePlainProduct(ProductDTO dto) {
                boolean requireDefaultVariant = false;
                Product newProduct;
                if (dto.getProductId() == null && dto.getConfigurable() == false)
                        requireDefaultVariant = true;
                if (dto.getProductId() != null)
                        newProduct = pRepository.saveAndFlush(toProduct(dto));
                else
                        newProduct = pRepository.save(toEntity(dto));
                if (requireDefaultVariant) {
                        ProductVariant variant = ProductVariant.builder()
                                        .sku(dto.getSku())
                                        .variantName(dto.getVariantName())
                                        .defaultVariant(dto.getDefaultVariant())
                                        .standardCost(dto.getStandardCost())
                                        .listPrice(dto.getListPrice())
                                        .currency(dto.getCurrency())
                                        .netWeight(dto.getNetWeight())
                                        .weightUomId(dto.getWeightUomId())
                                        .length(dto.getLength())
                                        .width(dto.getWidth())
                                        .height(dto.getHeight())
                                        .sizeUomId(dto.getSizeUomId())
                                        .productId(newProduct.getProductId())
                                        .createdBy(0L)
                                        .build();
                        variantRepository.save(variant);

                }
                return SimpleResponse.builder()
                                .code(200)
                                .message("SAVED::")
                                .build();
        }

        private Product toProduct(ProductDTO dto) {
                if (dto.getProductId() == null) {
                        return Product.builder()
                                        .productId(dto.getProductId())
                                        .productCode(dto.getProductCode())
                                        .productName(dto.getProductName())
                                        .productDescription(dto.getProductDescription())
                                        .type(dto.getType())
                                        .configurable(dto.getConfigurable())
                                        .status(dto.getStatus())
                                        .baseUomId(dto.getBaseUomId())
                                        .isEnabled(dto.getIsEnabled())
                                        .taxCategory(dto.getTaxCategory())
                                        .updatedBy(globalVariable.currentUserId())
                                        .build();
                } else {
                        Optional<Product> existed = pRepository.findById(dto.getProductId());
                        if (!existed.isPresent())
                                throw new RuntimeException("Can't find record in product_tb with product_id="
                                                + dto.getProductId());
                        Product product = existed.get();
                        product.setProductCode(dto.getProductCode());
                        product.setProductName(dto.getProductName());
                        product.setProductDescription(dto.getProductDescription());
                        product.setType(dto.getType());
                        product.setConfigurable(dto.getConfigurable());
                        product.setStatus(dto.getStatus());
                        product.setBaseUomId(dto.getBaseUomId());
                        product.setIsEnabled(dto.getIsEnabled());
                        product.setTaxCategory(dto.getTaxCategory());
                        return product;
                }
        }

        private ProductVariant toEntityWithVersion(ProductVariantDTO dto, Integer version) {
                return ProductVariant.builder()
                                .variantId(dto.getVariantId())
                                .productId(dto.getProductId())
                                .sku(dto.getSku())
                                .variantName(dto.getVariantName())
                                .defaultVariant(dto.getDefaultVariant())
                                .description(dto.getDescription())
                                .standardCost(dto.getStandardCost())
                                .listPrice(dto.getListPrice())
                                .currency(dto.getCurrency())
                                .netWeight(dto.getNetWeight())
                                .weightUomId(dto.getWeightUom())
                                .length(dto.getLength())
                                .width(dto.getWidth())
                                .height(dto.getHeight())
                                .sizeUomId(dto.getSizeUom())
                                .version(version)
                                .updatedBy(globalVariable.currentUserId())
                                .build();
        }

        private ProductVariant toEntity(ProductVariantDTO dto) {
                return ProductVariant.builder()
                                .variantId(dto.getVariantId())
                                .productId(dto.getProductId())
                                .sku(dto.getSku())
                                .variantName(dto.getVariantName())
                                .defaultVariant(dto.getDefaultVariant())
                                .description(dto.getDescription())
                                .standardCost(dto.getStandardCost())
                                .listPrice(dto.getListPrice())
                                .currency(dto.getCurrency())
                                .netWeight(dto.getNetWeight())
                                .weightUomId(dto.getWeightUom())
                                .length(dto.getLength())
                                .width(dto.getWidth())
                                .height(dto.getHeight())
                                .sizeUomId(dto.getSizeUom())
                                .createdBy(globalVariable.currentUserId())
                                .build();
        }

        public Product toEntity(ProductDTO product) {
                return Product.builder()
                                .productId(product.getProductId())
                                .productCode(product.getProductCode())
                                .productName(product.getProductName())
                                .productDescription(product.getProductDescription())
                                .type(product.getType())
                                .configurable(product.getConfigurable())
                                .status(product.getStatus())
                                .baseUomId(product.getBaseUomId())
                                // .standardCost(product.getStandardCost())
                                // .listPrice(product.getListPrice())
                                // .currency(product.getCurrency())
                                .taxCategory(product.getTaxCategory())
                                .createdBy(globalVariable.currentUserId())
                                .build();
        }

        public Product toEntityWithVersion(ProductDTO product, Integer version) {
                return Product.builder()
                                .productId(product.getProductId())
                                .productCode(product.getProductCode())
                                .productName(product.getProductName())
                                .productDescription(product.getProductDescription())
                                .type(product.getType())
                                .configurable(product.getConfigurable())
                                .status(product.getStatus())
                                .baseUomId(product.getBaseUomId())
                                .taxCategory(product.getTaxCategory())
                                .updatedBy(globalVariable.currentUserId())
                                .version(version)
                                .build();
        }

        @Transactional
        public SimpleResponse importProducts(MultipartFile file) {
                String line = "";
                List<ProductDTO> products = new ArrayList<>();
                byte currentLine = 0;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                        while ((line = br.readLine()) != null) {
                                if (line.trim().isEmpty() || currentLine < 1) {
                                        ++currentLine;
                                        continue;
                                }

                                String[] data = GlobalUtil.joinOneSentence(line);

                                ProductDTO product = ProductDTO.builder()
                                                .productId(data.length > 0 && data[0] != null && !data[0].isBlank()
                                                                ? Long.valueOf(data[0].trim())
                                                                : null)
                                                .productCode(data.length > 1 && data[1] != null ? data[1].trim()
                                                                : null)
                                                .productName(data.length > 2 && data[2] != null ? data[2].trim()
                                                                : null)
                                                .productDescription(
                                                                data.length > 3 && data[3] != null ? data[3].trim()
                                                                                : null)
                                                .type(data.length > 4 && data[4] != null && !data[4].isBlank()
                                                                ? ProductType.valueOf(data[4].trim())
                                                                : null)
                                                .configurable(data.length > 5 && data[5] != null && !data[5].isBlank()
                                                                ? Boolean.valueOf(data[5].trim())
                                                                : null)
                                                .status(data.length > 6 && data[6] != null && !data[6].isBlank()
                                                                ? ProductStatus.valueOf(data[6].trim())
                                                                : null)
                                                .baseUomId(data.length > 7 && data[7] != null && !data[7].isBlank()
                                                                ? Long.valueOf(data[7].trim())
                                                                : null)
                                                .isEnabled(data.length > 8 && data[8] != null && !data[8].isBlank()
                                                                ? Boolean.valueOf(data[8].trim())
                                                                : null)
                                                .taxCategory(data.length > 9 && data[9] != null ? data[9].trim()
                                                                : null)
                                                .sku(data.length > 10 && data[10] != null ? data[10].trim() : null)
                                                .variantName(data.length > 11 && !data[11].isBlank() ? data[11].trim()
                                                                : null)
                                                .defaultVariant(data.length > 12 && data[12] != null
                                                                && !data[12].isBlank()
                                                                                ? Boolean.valueOf(data[12].trim())
                                                                                : true)
                                                .standardCost(data.length > 13 && data[13] != null
                                                                && !data[13].isBlank()
                                                                                ? new BigDecimal(data[13].trim())
                                                                                : null)
                                                .listPrice(data.length > 14 && data[14] != null && !data[14].isBlank()
                                                                ? new BigDecimal(data[14].trim())
                                                                : null)
                                                .currency(data.length > 15 && data[15] != null ? data[15].trim()
                                                                : null)
                                                .netWeight(data.length > 16 && data[16] != null && !data[16].isBlank()
                                                                ? new BigDecimal(data[16].trim())
                                                                : null)
                                                .weightUomId(data.length > 17 && data[17] != null
                                                                && !data[17].trim().isEmpty() && !data[17].isBlank()
                                                                                ? Long.valueOf(data[17].trim())
                                                                                : null)
                                                .length(data.length > 18 && data[18] != null && !data[18].isBlank()
                                                                ? new BigDecimal(data[18].trim())
                                                                : null)
                                                .width(data.length > 19 && data[19] != null && !data[19].isBlank()
                                                                ? new BigDecimal(data[19].trim())
                                                                : null)
                                                .height(data.length > 20 && data[20] != null && !data[20].isBlank()
                                                                ? new BigDecimal(data[20].trim())
                                                                : null)
                                                .sizeUomId(data.length > 21 && data[21] != null && !data[21].isBlank()
                                                                ? Long.valueOf(data[21].trim())
                                                                : null)
                                                .build();
                                products.add(product);
                        }
                } catch (IOException e) {
                        log.error("Failed to convert file!\nException message:" + e.getMessage());
                }
                for (ProductDTO dto : products) {
                        saveOrUpdatePlainProduct(dto);
                }
                return SimpleResponse.builder()
                                .code(200)
                                .message("Uploaded::" + products.size())
                                .build();
        }

        public String export(BasicPage page, ProductFilter filter, HttpServletResponse resp) {

                try {
                        Page<ProductVariantCategoryView> result = viewRepository.findAll(
                                        ProductViewSepcification.filter(filter),
                                        PageRequest.of(page.getPageNumber(), page.getPageSize(), page.getSort()));

                        OutputStream out = resp.getOutputStream();
                } catch (IOException e) {
                        e.printStackTrace();
                }
                throw new UnsupportedOperationException("Unimplemented method 'export'");
        }
}

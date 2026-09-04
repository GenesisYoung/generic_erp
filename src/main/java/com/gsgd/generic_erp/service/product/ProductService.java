package com.gsgd.generic_erp.service.product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.annotations.AuditAction;
import com.gsgd.generic_erp.annotations.AuditRecord;
import com.gsgd.generic_erp.dto.ProductDTO;
import com.gsgd.generic_erp.dto.ProductVariantDTO;
import com.gsgd.generic_erp.dto.filter.ProductFilter;
import com.gsgd.generic_erp.dto.filter.ProductVariantFilter;
import com.gsgd.generic_erp.entity.product.Product;
import com.gsgd.generic_erp.entity.product.ProductVariant;
import com.gsgd.generic_erp.repository.product.ProductRepository;
import com.gsgd.generic_erp.repository.product.ProductVariantCategoryRepository;
import com.gsgd.generic_erp.repository.product.ProductVariantRepository;
import com.gsgd.generic_erp.spec.ProductViewSepcification;
import com.gsgd.generic_erp.spec.VariantSpecification;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.GlobalVariable;
import com.gsgd.generic_erp.util.SimplePageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;
import com.gsgd.generic_erp.view.sql.ProductVariantCategoryView;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

        private final ProductRepository pRepository;
        private final ProductVariantCategoryRepository viewRepository;
        private final ProductVariantRepository variantRepository;
        private final GlobalVariable globalVariable;

        @Cacheable(value = "productQuery", key = "#page.pageNumber+':'+#page.pageSize+':'+#filter.productCode+':'+#filter.productName+':'+#filter.sku+':'+#filter.cateId+':'+#filter.taxCategory+':'+#filter.type+':'+#filter.status+':'+#filter.listPrice+':'+minPrice+':'+maxPrice+':'+configurable")
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

        @Cacheable(value = "productQuery", key = "'productQueryId:'+#id")
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
        @AuditRecord(action = AuditAction.CREATEORUPDATE, module = "PRODUCT", targetId = "#result.message+#product.productId", description = "Create or update a product")
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

        public SimpleResponse disableProduct(Long[] ids) {
                return SimpleResponse.builder()
                                .code(200)
                                .message("CREATE::")
                                .build();
        }

        @Transactional
        @AuditRecord(action = AuditAction.CREATEORUPDATE, module = "PRODUCT", targetId = "#result.message+#product.variantId", description = "Create or update a variant")
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
                                .build();
        }

        @Cacheable(value = "productQuery", key = "#page.pageNumber+':'+#page.pageSize+':'+#filter.variantId+':'+#filter.productId+':'+#filter.sku+':'+#filter.variantName+':'+#filter.isDefault+':'+#filter.max+':'+#filter.min")
        public SimplePageResponse queryVariants(ProductVariantFilter filter, BasicPage page) {
                Page<ProductVariant> variants = variantRepository.findAll(VariantSpecification.filter(
                                filter),
                                PageRequest.of(page.getPageNumber(), page.getPageSize()));
                return new SimplePageResponse(page.getPageNumber(), page.getPageSize(), variants.getTotalElements(),
                                variants.getTotalPages(), variants.getContent());
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
                                // .standardCost(product.getStandardCost())
                                // .listPrice(product.getListPrice())
                                // .currency(product.getCurrency())
                                .taxCategory(product.getTaxCategory())
                                .updatedBy(globalVariable.currentUserId())
                                .version(version)
                                .build();
        }
}

package com.gsgd.generic_erp.service.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.annotations.AuditAction;
import com.gsgd.generic_erp.annotations.AuditRecord;
import com.gsgd.generic_erp.dto.ProductDTO;
import com.gsgd.generic_erp.dto.ProductFilter;
import com.gsgd.generic_erp.entity.product.Product;
import com.gsgd.generic_erp.repository.product.ProductRepository;
import com.gsgd.generic_erp.repository.product.ProductVariantCategoryRepository;
import com.gsgd.generic_erp.spec.ProductViewSepcification;
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
        private final GlobalVariable globalVariable;

        public SimplePageResponse fetchProducts(boolean isMinPrice, boolean isMaxprice, ProductFilter filter,
                        BasicPage page) {
                Page<ProductVariantCategoryView> result = viewRepository.findAll(
                                ProductViewSepcification.filter(isMinPrice, isMaxprice, filter),
                                PageRequest.of(page.getPageNumber(), page.getPageSize(), page.getSort()));
                return SimplePageResponse.builder()
                                .content(result.getContent())
                                .pageNumber(result.getNumber())
                                .pageSize(result.getSize())
                                .totalElements(result.getTotalElements())
                                .totalPages(result.getTotalPages())
                                .build();
        }

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
        public SimpleResponse saveProduct(ProductDTO product) {
                if (product.getProductId() == null) {
                        pRepository.save(toEntity(product));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("CREATE::")
                                        .build();
                } else {
                        Product p = pRepository.findById(product.getProductId()).get();
                        pRepository.saveAndFlush(toEntityWithVersion(product, p.getVersion()));
                        return SimpleResponse.builder()
                                        .code(200)
                                        .message("UPDATE::")
                                        .build();
                }
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
                                .standardCost(product.getStandardCost())
                                .listPrice(product.getListPrice())
                                .currency(product.getCurrency())
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
                                .standardCost(product.getStandardCost())
                                .listPrice(product.getListPrice())
                                .currency(product.getCurrency())
                                .taxCategory(product.getTaxCategory())
                                .updatedBy(globalVariable.currentUserId())
                                .version(version)
                                .build();
        }
}

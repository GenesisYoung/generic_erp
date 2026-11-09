package com.gsgd.generic_erp.service.product;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.entity.product.Category;
import com.gsgd.generic_erp.repository.product.ProductCategoryRepository;
import com.gsgd.generic_erp.spec.ProductCategorySpecification;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class ProductCatrgoryService {
    private final ProductCategoryRepository categoryRepository;

    public BasicResponse getFullTree() {
        List<Category> categories = categoryRepository.findAll(ProductCategorySpecification.enabled(),
                Sort.by("depth").ascending());
        return BasicResponse.builder()
                .message("Success")
                .status(1)
                .object(buildTree(categories))
                .build();
    }

    private List<CategoryResponse> buildTree(List<Category> list) {
        Map<Long, Long[]> positions = new Hashtable<>();
        List<CategoryResponse> result = new ArrayList<>();
        int current = 0;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(0, list.get(current).getDepth())) {
                return null;
            }
        }
        return result;
    }
}

record CategoryResponse(Category ele, List<Category> children) {
};
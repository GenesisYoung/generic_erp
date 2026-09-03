package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {
    private Long cateId;
    private String cateName;
    private String cateAbbr;
    private String description;
    private Long parentId;
    private String pathCache;
    private Integer depth = 0;
}

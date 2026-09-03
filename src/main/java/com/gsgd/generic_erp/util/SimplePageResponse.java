package com.gsgd.generic_erp.util;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimplePageResponse {
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private List<?> content;
}

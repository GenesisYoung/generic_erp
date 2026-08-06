package com.gsgd.generic_erp.util;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.NoArgsConstructor;

/**
 * A basic page response wrapper for handling paginated data.
 * T - The type of the entity being paginated.
 * R - The type of the DTO (Data Transfer Object) that will be returned in the
 * response.
 */
@NoArgsConstructor
public class BasicPageResponse<T, R> {

    @JsonProperty("content")
    private List<R> content;
    @JsonProperty("pageNumber")
    private int pageNumber;
    @JsonProperty("pageSize")
    private int pageSize;
    @JsonProperty("totalElements")
    private long totalElements;
    @JsonProperty("totalPages")
    private int totalPages;

    public BasicPageResponse(List<R> content, Page<T> page) {
        this.content = content;
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
    }

    public List<R> getContent() {
        return content;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    // getters

}
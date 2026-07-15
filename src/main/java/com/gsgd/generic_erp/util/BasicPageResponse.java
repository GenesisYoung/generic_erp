package com.gsgd.generic_erp.util;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A basic page response wrapper for handling paginated data.
 * T - The type of the entity being paginated.
 * R - The type of the DTO (Data Transfer Object) that will be returned in the
 * response.
 */
public class BasicPageResponse<T, R> {

    private List<R> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
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
package com.gsgd.generic_erp.util;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A basic page response wrapper for handling paginated data.
 * T - The type of the entity being paginated.
 * R - The type of the DTO (Data Transfer Object) that will be returned in the
 * response.
 */
@Getter
@Setter
@NoArgsConstructor
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

    /**
     * Used to deserialization for Redis cache to avoid it chooosing
     * BasicPageResponse(List<R> content, Page<T> page)
     */
    @JsonCreator
    public BasicPageResponse(
            @JsonProperty("content") List<R> content,
            @JsonProperty("pageNumber") int pageNumber,
            @JsonProperty("pageSize") int pageSize,
            @JsonProperty("totalElements") long totalElements,
            @JsonProperty("totalPages") int totalPages) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}
package com.gsgd.generic_erp.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Generic request body for paginated queries: page index, page size, and a
 * typed filter object {@code T} specific to the endpoint.
 */
@Data
@AllArgsConstructor
public class BasicPageRequest<T> {
    private int page;
    private int size;
    private T filter;
}

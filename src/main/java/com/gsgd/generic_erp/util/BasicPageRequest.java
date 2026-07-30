package com.gsgd.generic_erp.util;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BasicPageRequest<T> {
    private int page;
    private int size;
    private T filter;
}

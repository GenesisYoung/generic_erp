package com.gsgd.generic_erp.util;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class BasicPage implements Pageable {

    private int pageNumber = 0;
    private int pageSize = 20;
    private Sort sort = Sort.unsorted();

    public BasicPage() {
    }

    public BasicPage(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    private BasicPage(int pageNumber, int pageSize, Sort sort) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sort = sort;
    }

    public void defineSort(Sort sort) {
        this.sort = sort;
    }

    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return (long) pageNumber * pageSize;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new BasicPage(pageNumber + 1, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return pageNumber > 0 ? new BasicPage(pageNumber - 1, pageSize, sort) : this;
    }

    @Override
    public Pageable first() {
        return new BasicPage(0, pageSize, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new BasicPage(pageNumber, pageSize, sort);
    }

    @Override
    public boolean hasPrevious() {
        return pageNumber > 0;
    }

}

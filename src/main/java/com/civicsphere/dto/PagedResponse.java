package com.civicsphere.dto;

import java.util.List;

public class PagedResponse<T> {
    private final List<T> items;
    private final long totalCount;
    private final int page;
    private final int pageSize;
    private final int totalPages;

    public PagedResponse(List<T> items, long totalCount, int page, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = Math.max(1, (int) Math.ceil((double) totalCount / Math.max(1, pageSize)));
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }
}


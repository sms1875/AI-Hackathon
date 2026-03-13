package com.gamepaper.api.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int currentPage;
    private final int size;

    public PagedResponse(Page<T> page) {
        this.content = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.currentPage = page.getNumber();
        this.size = page.getSize();
    }
}

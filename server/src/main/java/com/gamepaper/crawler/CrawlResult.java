package com.gamepaper.crawler;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 크롤링 결과 DTO
 */
@Getter
@Builder
public class CrawlResult {

    private final boolean success;
    private final int collectedCount;
    private final String errorMessage;

    public static CrawlResult success(int count) {
        return CrawlResult.builder()
                .success(true)
                .collectedCount(count)
                .build();
    }

    public static CrawlResult failure(String errorMessage) {
        return CrawlResult.builder()
                .success(false)
                .collectedCount(0)
                .errorMessage(errorMessage)
                .build();
    }
}

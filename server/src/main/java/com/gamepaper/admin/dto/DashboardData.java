package com.gamepaper.admin.dto;

import com.gamepaper.domain.crawler.CrawlingLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class DashboardData {
    private final long totalGames;
    private final long totalWallpapers;
    private final LocalDateTime lastCrawledAt;   // 전체 게임 중 가장 최근 크롤링 시각
    private final long activeCount;
    private final long updatingCount;
    private final long failedCount;
    private final List<CrawlingLog> recentLogs;  // 최근 10건
}

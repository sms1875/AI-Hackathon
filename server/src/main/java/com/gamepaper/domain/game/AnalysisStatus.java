package com.gamepaper.domain.game;

/**
 * AI 파싱 전략 분석 상태.
 * PENDING → ANALYZING → COMPLETED | FAILED
 */
public enum AnalysisStatus {
    PENDING,    // 분석 대기 중
    ANALYZING,  // AI 분석 진행 중 (Selenium HTML 수집 + Claude API 호출)
    COMPLETED,  // 분석 완료, CrawlerStrategy 저장됨
    FAILED      // 분석 실패 (HTML 수집 실패 또는 Claude API 오류)
}

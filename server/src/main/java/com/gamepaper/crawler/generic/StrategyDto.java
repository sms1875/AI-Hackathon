package com.gamepaper.crawler.generic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * CrawlerStrategy JSON을 자바 객체로 역직렬화하는 DTO.
 * 알 수 없는 필드는 무시 (스키마 버전 확장 대비).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyDto {

    /** 이미지를 선택하는 CSS 셀렉터 */
    private String imageSelector;

    /** 이미지 URL을 추출할 속성 (src, data-src, href) */
    private String imageAttribute = "src";

    /** 페이지네이션 타입: none | button_click | scroll | url_pattern */
    private String paginationType = "none";

    /** 다음 페이지 버튼 셀렉터 (button_click일 때) */
    private String nextButtonSelector;

    /** 페이지 URL 패턴 (url_pattern일 때, {page}를 페이지 번호로) */
    private String urlPattern;

    /** 최대 수집 페이지 수 (기본 5) */
    private int maxPages = 5;

    /** 페이지 로딩 대기 시간 ms (기본 2000) */
    private int waitMs = 2000;

    /** 실행기 타임아웃 초 (기본 30) */
    private int timeoutSeconds = 30;

    /** 페이지 접속 전 사전 동작 목록 */
    private List<Map<String, String>> preActions;

    /**
     * 크롤링 중단 조건.
     * 예: "duplicate_count:10" - 중복 이미지 10개 연속 수집 시 중단
     */
    private String stopCondition;
}

package com.gamepaper.claude.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 분석 결과 응답 DTO.
 */
@Getter
@RequiredArgsConstructor
public class AnalyzeResponse {
    private final JsonNode strategy; // 파싱된 파싱 전략 JSON
    private final String rawJson;    // 원본 JSON 문자열
}

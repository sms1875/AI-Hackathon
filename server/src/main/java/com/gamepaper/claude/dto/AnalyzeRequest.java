package com.gamepaper.claude.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 UI에서 받는 AI 분석 요청 DTO.
 */
@Getter
@Setter
@NoArgsConstructor
public class AnalyzeRequest {
    private String url;
    private Long gameId; // nullable (신규 등록 시 null)
}

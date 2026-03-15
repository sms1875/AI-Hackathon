package com.gamepaper.admin;

import com.gamepaper.claude.ClaudeApiClient;
import com.gamepaper.claude.HtmlFetcher;
import com.gamepaper.claude.dto.AnalyzeRequest;
import com.gamepaper.claude.dto.AnalyzeResponse;
import com.gamepaper.domain.strategy.CrawlerStrategy;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 관리자 UI AI 분석 REST API.
 * POST /admin/api/analyze — URL을 받아 HTML을 가져오고 Claude API로 파싱 전략 생성.
 * API 키 미설정 시 데모 전략 반환 (개발/테스트 환경 지원).
 */
@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminAnalyzeApiController {

    private final ClaudeApiClient claudeApiClient;
    private final HtmlFetcher htmlFetcher;
    private final CrawlerStrategyRepository strategyRepository;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody AnalyzeRequest request) {
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL이 필요합니다."));
        }

        String pageUrl = request.getUrl().trim();
        log.info("AI 분석 시작 - url={}, gameId={}", pageUrl, request.getGameId());

        // 1. HTML 수집 (타임아웃 10초)
        String html;
        try {
            html = htmlFetcher.fetch(pageUrl);
        } catch (IOException e) {
            log.error("HTML 수집 실패 - url={}", pageUrl, e);
            return ResponseEntity.badRequest().body(
                    Map.of("error", "페이지 접근 실패: " + e.getMessage())
            );
        }

        // 2. Claude API 호출
        AnalyzeResponse response;
        try {
            response = claudeApiClient.analyzeHtml(html, pageUrl);
        } catch (IllegalStateException e) {
            // API 키 미설정 — 데모 전략 반환
            log.warn("Claude API 키 미설정 - 데모 전략 반환");
            return ResponseEntity.ok(Map.of(
                    "strategy", buildDemoStrategy(),
                    "warning", "ANTHROPIC_API_KEY가 설정되지 않아 데모 전략을 반환합니다."
            ));
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "AI 분석 실패: " + e.getMessage())
            );
        }

        // 3. CrawlerStrategy 저장 (gameId가 있는 경우)
        if (request.getGameId() != null) {
            int nextVersion = strategyRepository
                    .findTopByGameIdOrderByVersionDesc(request.getGameId())
                    .map(s -> s.getVersion() + 1)
                    .orElse(1);

            CrawlerStrategy strategy = new CrawlerStrategy(
                    request.getGameId(), response.getRawJson());
            strategy.setVersion(nextVersion);
            strategyRepository.save(strategy);
            log.info("전략 저장 완료 - gameId={}, version={}", request.getGameId(), nextVersion);
        }

        return ResponseEntity.ok(Map.of("strategy", response.getStrategy()));
    }

    /**
     * API 키 미설정 시 반환하는 데모 전략.
     * 개발/테스트 환경에서 UI 동작 확인용.
     */
    private Map<String, Object> buildDemoStrategy() {
        return Map.of(
                "imageSelector", ".wallpaper-list img, .wallpaper img",
                "imageAttribute", "src",
                "paginationType", "button_click",
                "nextButtonSelector", ".pagination .next",
                "maxPages", 5,
                "waitMs", 2000,
                "preActions", List.of()
        );
    }
}

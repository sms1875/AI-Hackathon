package com.gamepaper.admin;

import com.gamepaper.claude.AnalysisService;
import com.gamepaper.claude.ClaudeApiClient;
import com.gamepaper.claude.HtmlFetcher;
import com.gamepaper.domain.game.AnalysisStatus;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.strategy.CrawlerStrategy;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 AI 분석 REST API.
 *
 * POST /admin/games/{id}/analyze       - 비동기 AI 분석 시작
 * GET  /admin/games/{id}/analyze/status - 분석 상태 polling
 * POST /admin/api/analyze              - 기존 URL 기반 즉시 분석 (하위 호환 유지)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminAnalyzeApiController {

    private final AnalysisService analysisService;
    private final GameRepository gameRepository;
    private final CrawlerStrategyRepository strategyRepository;
    private final ClaudeApiClient claudeApiClient;
    private final HtmlFetcher htmlFetcher;

    /**
     * 게임 ID 기반 AI 분석 트리거 (비동기).
     * 즉시 202 Accepted 반환, 분석은 백그라운드에서 진행.
     */
    @PostMapping("/admin/games/{id}/analyze")
    public ResponseEntity<Map<String, Object>> triggerAnalysis(@PathVariable Long id) {
        Optional<Game> gameOpt = gameRepository.findById(id);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOpt.get();
        // 이미 분석 중이면 중복 실행 방지
        if (game.getAnalysisStatus() == AnalysisStatus.ANALYZING) {
            return ResponseEntity.ok(Map.of(
                    "status", "ANALYZING",
                    "message", "이미 분석이 진행 중입니다."
            ));
        }

        analysisService.analyzeAsync(id);
        log.info("AI 분석 트리거 - gameId={}", id);

        return ResponseEntity.accepted().body(Map.of(
                "status", "ANALYZING",
                "message", "AI 분석을 시작했습니다. /admin/games/" + id + "/analyze/status 로 상태를 확인하세요."
        ));
    }

    /**
     * AI 분석 상태 조회 (프론트엔드 polling용).
     * 2초 간격으로 호출하여 COMPLETED 또는 FAILED 상태가 될 때까지 반복.
     */
    @GetMapping("/admin/games/{id}/analyze/status")
    public ResponseEntity<Map<String, Object>> getAnalysisStatus(@PathVariable Long id) {
        Optional<Game> gameOpt = gameRepository.findById(id);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOpt.get();
        AnalysisStatus status = game.getAnalysisStatus();

        Map<String, Object> response;

        if (status == AnalysisStatus.COMPLETED) {
            // 최신 전략 JSON 포함하여 반환
            Optional<CrawlerStrategy> strategyOpt =
                    strategyRepository.findTopByGameIdOrderByVersionDesc(id);

            response = Map.of(
                    "status", status.name(),
                    "strategy", strategyOpt.map(CrawlerStrategy::getStrategyJson).orElse("{}"),
                    "version", strategyOpt.map(CrawlerStrategy::getVersion).orElse(0)
            );
        } else if (status == AnalysisStatus.FAILED) {
            response = Map.of(
                    "status", status.name(),
                    "error", game.getAnalysisError() != null ? game.getAnalysisError() : "알 수 없는 오류"
            );
        } else {
            response = Map.of(
                    "status", status.name(),
                    "message", status == AnalysisStatus.ANALYZING
                            ? "AI가 파싱 전략을 생성하고 있습니다..." : "분석 대기 중"
            );
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 기존 URL 기반 즉시 분석 API (하위 호환 유지).
     * 게임 등록 전 미리보기용으로 사용.
     */
    @PostMapping("/admin/api/analyze")
    public ResponseEntity<?> analyzeByUrl(
            @RequestBody com.gamepaper.claude.dto.AnalyzeRequest request) {
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL이 필요합니다."));
        }

        // gameId가 있으면 비동기 AnalysisService 위임
        if (request.getGameId() != null) {
            analysisService.analyzeAsync(request.getGameId());
            return ResponseEntity.accepted().body(Map.of(
                    "message", "분석을 시작했습니다.",
                    "statusUrl", "/admin/games/" + request.getGameId() + "/analyze/status"
            ));
        }

        // gameId 없이 URL만 있는 경우: 동기 처리 (게임 등록 전 미리보기)
        String pageUrl = request.getUrl().trim();
        log.info("URL 기반 즉시 분석 - url={}", pageUrl);

        String html;
        try {
            html = htmlFetcher.fetch(pageUrl);
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "페이지 접근 실패: " + e.getMessage()));
        }

        try {
            com.gamepaper.claude.dto.AnalyzeResponse response =
                    claudeApiClient.analyzeHtml(html, pageUrl);
            return ResponseEntity.ok(Map.of("strategy", response.getStrategy()));
        } catch (IllegalStateException e) {
            // API 키 미설정 — 데모 전략 반환
            return ResponseEntity.ok(Map.of(
                    "strategy", buildDemoStrategy(),
                    "warning", "ANTHROPIC_API_KEY가 설정되지 않아 데모 전략을 반환합니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "AI 분석 실패: " + e.getMessage()));
        }
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

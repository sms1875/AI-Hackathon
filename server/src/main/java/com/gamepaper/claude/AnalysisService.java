package com.gamepaper.claude;

import com.gamepaper.claude.dto.AnalyzeResponse;
import com.gamepaper.domain.game.AnalysisStatus;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.strategy.CrawlerStrategy;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * AI 파싱 전략 분석 서비스.
 * 비동기(@Async)로 HTML 수집 → Claude API 호출 → 전략 저장 파이프라인 실행.
 *
 * 상태 전이: PENDING → ANALYZING → COMPLETED | FAILED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ClaudeApiClient claudeApiClient;
    private final HtmlFetcher htmlFetcher;
    private final GameRepository gameRepository;
    private final CrawlerStrategyRepository strategyRepository;

    /**
     * 비동기로 AI 분석 파이프라인을 실행합니다.
     * 호출 즉시 반환되며, 분석 완료 시 Game.analysisStatus가 업데이트됩니다.
     *
     * @param gameId 분석할 게임 ID
     */
    @Async("asyncExecutor")
    public void analyzeAsync(Long gameId) {
        log.info("AI 분석 시작 - gameId={}", gameId);

        // 1. 상태를 ANALYZING으로 변경
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            log.error("게임을 찾을 수 없음 - gameId={}", gameId);
            return;
        }
        game.setAnalysisStatus(AnalysisStatus.ANALYZING);
        game.setAnalysisError(null);
        gameRepository.save(game);

        try {
            // 2. HTML 수집
            String html = htmlFetcher.fetch(game.getUrl());
            log.debug("HTML 수집 완료 - gameId={}, 길이={}", gameId, html.length());

            // 3. Claude API 호출 → 파싱 전략 생성
            AnalyzeResponse response;
            try {
                response = claudeApiClient.analyzeHtml(html, game.getUrl());
            } catch (IllegalStateException e) {
                // API 키 미설정 시 데모 전략 사용
                log.warn("Claude API 키 미설정 - 데모 전략으로 대체 - gameId={}", gameId);
                response = buildDemoResponse();
            }

            // 4. 버전 계산 (기존 전략이 있으면 +1)
            int nextVersion = strategyRepository
                    .findTopByGameIdOrderByVersionDesc(gameId)
                    .map(s -> s.getVersion() + 1)
                    .orElse(1);

            // 5. CrawlerStrategy 저장
            CrawlerStrategy strategy = new CrawlerStrategy(gameId, response.getRawJson());
            strategy.setVersion(nextVersion);
            strategyRepository.save(strategy);

            // 6. 상태를 COMPLETED로 변경
            game.setAnalysisStatus(AnalysisStatus.COMPLETED);
            gameRepository.save(game);

            log.info("AI 분석 완료 - gameId={}, version={}", gameId, nextVersion);

        } catch (IOException e) {
            log.error("HTML 수집 실패 - gameId={}", gameId, e);
            markFailed(gameId, "페이지 접근 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("AI 분석 실패 - gameId={}", gameId, e);
            markFailed(gameId, "AI 분석 실패: " + e.getMessage());
        }
    }

    /**
     * 분석 상태를 FAILED로 변경하고 오류 메시지를 저장합니다.
     */
    private void markFailed(Long gameId, String errorMessage) {
        gameRepository.findById(gameId).ifPresent(game -> {
            game.setAnalysisStatus(AnalysisStatus.FAILED);
            // 오류 메시지 500자 제한
            game.setAnalysisError(errorMessage.length() > 500
                    ? errorMessage.substring(0, 500) : errorMessage);
            gameRepository.save(game);
        });
    }

    /**
     * API 키 미설정 시 반환하는 데모 전략.
     */
    private AnalyzeResponse buildDemoResponse() {
        String demoJson = """
                {
                  "imageSelector": ".wallpaper-list img, .wallpaper img",
                  "imageAttribute": "src",
                  "paginationType": "button_click",
                  "nextButtonSelector": ".pagination .next",
                  "maxPages": 5,
                  "waitMs": 2000,
                  "preActions": []
                }
                """;
        try {
            com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(demoJson);
            return new AnalyzeResponse(node, demoJson.trim());
        } catch (Exception e) {
            throw new RuntimeException("데모 전략 생성 실패", e);
        }
    }
}

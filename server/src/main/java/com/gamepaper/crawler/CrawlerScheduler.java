package com.gamepaper.crawler;

import com.gamepaper.crawler.generic.GenericCrawlerExecutor;
import com.gamepaper.domain.crawler.CrawlingLog;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.crawler.CrawlingLogStatus;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.game.GameStatus;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 크롤러 스케줄러.
 * GenericCrawlerExecutor 기반으로 전략이 있는 게임을 주기적으로 크롤링합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final GameRepository gameRepository;
    private final CrawlingLogRepository crawlingLogRepository;
    private final GenericCrawlerExecutor genericCrawlerExecutor;
    private final CrawlerStrategyRepository strategyRepository;

    /**
     * 6시간 주기 크롤링.
     * 전략이 있는 모든 게임에 대해 GenericCrawlerExecutor 실행.
     * INACTIVE 상태 게임은 건너뜁니다.
     */
    @Scheduled(fixedDelayString = "${crawler.schedule.delay-ms:21600000}")
    public void runAll() {
        log.info("크롤링 스케줄 시작");

        List<Game> allGames = gameRepository.findAll();
        for (Game game : allGames) {
            if (game.getStatus() == GameStatus.INACTIVE) continue;

            strategyRepository.findTopByGameIdOrderByVersionDesc(game.getId())
                    .ifPresent(strategy ->
                            runGeneric(game.getId(), game.getUrl(), strategy.getStrategyJson()));
        }

        log.info("크롤링 스케줄 완료");
    }

    /**
     * GenericCrawlerExecutor로 단일 게임 비동기 크롤링.
     */
    @Async("asyncExecutor")
    public void runGenericAsync(Long gameId, String gameUrl, String strategyJson) {
        runGeneric(gameId, gameUrl, strategyJson);
    }

    /**
     * GenericCrawlerExecutor 동기 실행 (스케줄러에서 사용).
     */
    public void runGeneric(Long gameId, String gameUrl, String strategyJson) {
        CrawlingLog logEntry = new CrawlingLog();
        logEntry.setGameId(gameId);
        logEntry.setStartedAt(LocalDateTime.now());
        logEntry.setStatus(CrawlingLogStatus.RUNNING);
        crawlingLogRepository.save(logEntry);

        gameRepository.findById(gameId).ifPresent(game -> {
            game.setStatus(GameStatus.UPDATING);
            gameRepository.save(game);
        });

        try {
            CrawlResult result = genericCrawlerExecutor.execute(gameId, gameUrl, strategyJson);
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setCollectedCount(result.getCollectedCount());

            if (result.isSuccess()) {
                logEntry.setStatus(CrawlingLogStatus.SUCCESS);
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus(GameStatus.ACTIVE);
                    game.setLastCrawledAt(LocalDateTime.now());
                    gameRepository.save(game);
                });
                log.info("GenericCrawler 성공 - gameId={}, 수집={}", gameId, result.getCollectedCount());
            } else {
                logEntry.setStatus(CrawlingLogStatus.FAILED);
                logEntry.setErrorMessage(result.getErrorMessage());
                handleCrawlFailure(gameId);
                log.error("GenericCrawler 실패 - gameId={}, 오류={}", gameId, result.getErrorMessage());
            }
        } catch (Exception e) {
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setStatus(CrawlingLogStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            handleCrawlFailure(gameId);
            log.error("GenericCrawler 예외 - gameId={}", gameId, e);
        } finally {
            crawlingLogRepository.save(logEntry);
        }
    }

    /**
     * 크롤링 실패 처리: 연속 3회 실패 시 게임 상태를 FAILED로 전환.
     */
    private void handleCrawlFailure(Long gameId) {
        // 최근 3건 로그 조회
        List<CrawlingLog> recentLogs = crawlingLogRepository
                .findTop3ByGameIdOrderByStartedAtDesc(gameId);

        boolean allFailed = recentLogs.size() >= 3 &&
                recentLogs.stream().allMatch(l -> l.getStatus() == CrawlingLogStatus.FAILED);

        gameRepository.findById(gameId).ifPresent(game -> {
            game.setStatus(GameStatus.FAILED);
            if (allFailed) {
                log.warn("연속 3회 크롤링 실패 - gameId={}, 상태를 FAILED로 전환", gameId);
            }
            gameRepository.save(game);
        });
    }
}

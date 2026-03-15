package com.gamepaper.crawler;

import com.gamepaper.crawler.generic.GenericCrawlerExecutor;
import com.gamepaper.domain.crawler.CrawlingLog;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.crawler.CrawlingLogStatus;
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
 * 전략이 있는 게임은 GenericCrawlerExecutor, 없는 게임은 기존 GameCrawler로 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final List<GameCrawler> crawlers;
    private final GameRepository gameRepository;
    private final CrawlingLogRepository crawlingLogRepository;
    private final GenericCrawlerExecutor genericCrawlerExecutor;
    private final CrawlerStrategyRepository strategyRepository;

    /**
     * 6시간 주기 크롤링.
     * 전략이 있는 게임 → GenericCrawlerExecutor
     * 전략이 없는 게임 → 기존 GameCrawler (fallback)
     */
    @Scheduled(fixedDelayString = "${crawler.schedule.delay-ms:21600000}")
    public void runAll() {
        log.info("크롤링 스케줄 시작");

        // 1. 전략이 있는 게임: GenericCrawlerExecutor 실행
        List<com.gamepaper.domain.game.Game> allGames = gameRepository.findAll();
        for (com.gamepaper.domain.game.Game game : allGames) {
            if (game.getStatus() == GameStatus.INACTIVE) continue;

            strategyRepository.findTopByGameIdOrderByVersionDesc(game.getId())
                    .ifPresent(strategy ->
                            runGeneric(game.getId(), game.getUrl(), strategy.getStrategyJson()));
        }

        // 2. 전략이 없는 게임: 기존 크롤러 fallback
        for (GameCrawler crawler : crawlers) {
            boolean hasStrategy = strategyRepository
                    .findTopByGameIdOrderByVersionDesc(crawler.getGameId())
                    .isPresent();
            if (!hasStrategy) {
                runSingle(crawler);
            }
        }

        log.info("크롤링 스케줄 완료");
    }

    /**
     * 비동기 단일 크롤러 실행 (I-1 해소: new Thread() 대체)
     */
    @Async("asyncExecutor")
    public void runSingleAsync(GameCrawler crawler) {
        runSingle(crawler);
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

    public void runSingle(GameCrawler crawler) {
        Long gameId = crawler.getGameId();
        CrawlingLog logEntry = new CrawlingLog();
        logEntry.setGameId(gameId);
        logEntry.setStartedAt(LocalDateTime.now());
        logEntry.setStatus(CrawlingLogStatus.RUNNING);
        crawlingLogRepository.save(logEntry);

        // 게임 상태 UPDATING으로 변경
        gameRepository.findById(gameId).ifPresent(game -> {
            game.setStatus(GameStatus.UPDATING);
            gameRepository.save(game);
        });

        try {
            CrawlResult result = crawler.crawl();
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setCollectedCount(result.getCollectedCount());

            if (result.isSuccess()) {
                logEntry.setStatus(CrawlingLogStatus.SUCCESS);
                // 게임 상태 ACTIVE, 마지막 크롤링 시각 업데이트
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus(GameStatus.ACTIVE);
                    game.setLastCrawledAt(LocalDateTime.now());
                    gameRepository.save(game);
                });
                log.info("크롤링 성공 - gameId={}, 수집={}", gameId, result.getCollectedCount());
            } else {
                logEntry.setStatus(CrawlingLogStatus.FAILED);
                logEntry.setErrorMessage(result.getErrorMessage());
                handleCrawlFailure(gameId);
                log.error("크롤링 실패 - gameId={}, 오류={}", gameId, result.getErrorMessage());
            }
        } catch (Exception e) {
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setStatus(CrawlingLogStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            handleCrawlFailure(gameId);
            log.error("크롤링 예외 - gameId={}", gameId, e);
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
            if (allFailed) {
                game.setStatus(GameStatus.FAILED);
                log.warn("연속 3회 크롤링 실패 - gameId={}, 상태를 FAILED로 전환", gameId);
            } else {
                game.setStatus(GameStatus.FAILED);  // 단순 실패도 FAILED
            }
            gameRepository.save(game);
        });
    }
}

package com.gamepaper.crawler;

import com.gamepaper.domain.crawler.CrawlingLog;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.crawler.CrawlingLogStatus;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.game.GameStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 크롤러 스케줄러.
 * Sprint 4에서 GenericCrawlerExecutor 교체 시 이 스케줄러를 재활용함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final List<GameCrawler> crawlers;
    private final GameRepository gameRepository;
    private final CrawlingLogRepository crawlingLogRepository;

    /**
     * 6시간 주기 크롤링 (테스트 시 fixedDelay로 단축 가능)
     */
    @Scheduled(fixedDelayString = "${crawler.schedule.delay-ms:21600000}")
    public void runAll() {
        log.info("크롤링 스케줄 시작 - 크롤러 수: {}", crawlers.size());
        for (GameCrawler crawler : crawlers) {
            runSingle(crawler);
        }
        log.info("크롤링 스케줄 완료");
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
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus(GameStatus.FAILED);
                    gameRepository.save(game);
                });
                log.error("크롤링 실패 - gameId={}, 오류={}", gameId, result.getErrorMessage());
            }
        } catch (Exception e) {
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setStatus(CrawlingLogStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            gameRepository.findById(gameId).ifPresent(game -> {
                game.setStatus(GameStatus.FAILED);
                gameRepository.save(game);
            });
            log.error("크롤링 예외 - gameId={}", gameId, e);
        } finally {
            crawlingLogRepository.save(logEntry);
        }
    }
}

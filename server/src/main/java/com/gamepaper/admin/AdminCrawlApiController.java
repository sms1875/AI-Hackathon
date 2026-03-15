package com.gamepaper.admin;

import com.gamepaper.crawler.CrawlerScheduler;
import com.gamepaper.crawler.GameCrawler;
import com.gamepaper.crawler.generic.GenericCrawlerExecutor;
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

@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminCrawlApiController {

    private final List<GameCrawler> crawlers;
    private final CrawlerScheduler crawlerScheduler;
    private final GameRepository gameRepository;
    private final CrawlerStrategyRepository strategyRepository;
    private final GenericCrawlerExecutor genericCrawlerExecutor;

    /**
     * 특정 게임 즉시 크롤링 트리거.
     * 1순위: CrawlerStrategy가 있으면 GenericCrawlerExecutor 사용
     * 2순위: 기존 GameCrawler 목록에서 gameId 일치하는 크롤러 사용
     */
    @PostMapping("/games/{id}/crawl")
    public ResponseEntity<Map<String, String>> triggerCrawl(@PathVariable Long id) {
        Optional<Game> gameOpt = gameRepository.findById(id);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOpt.get();

        // 1순위: GenericCrawlerExecutor (전략이 있는 경우)
        Optional<CrawlerStrategy> strategyOpt =
                strategyRepository.findTopByGameIdOrderByVersionDesc(id);

        if (strategyOpt.isPresent()) {
            CrawlerStrategy strategy = strategyOpt.get();
            crawlerScheduler.runGenericAsync(id, game.getUrl(), strategy.getStrategyJson());
            log.info("GenericCrawlerExecutor 크롤링 트리거 - gameId={}", id);
            return ResponseEntity.ok(Map.of("message", "GenericCrawlerExecutor로 크롤링을 시작했습니다."));
        }

        // 2순위: 기존 GameCrawler (하위 호환)
        GameCrawler target = crawlers.stream()
                .filter(c -> c.getGameId().equals(id))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "AI 분석 후 전략을 생성하거나, 기존 크롤러를 확인하세요."
            ));
        }

        crawlerScheduler.runSingleAsync(target);
        log.info("기존 크롤러 트리거 - gameId={}", id);
        return ResponseEntity.ok(Map.of("message", "크롤링을 시작했습니다."));
    }
}

package com.gamepaper.admin;

import com.gamepaper.crawler.CrawlerScheduler;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.strategy.CrawlerStrategy;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminCrawlApiController {

    private final CrawlerScheduler crawlerScheduler;
    private final GameRepository gameRepository;
    private final CrawlerStrategyRepository strategyRepository;

    /**
     * 특정 게임 즉시 크롤링 트리거.
     * CrawlerStrategy가 있으면 GenericCrawlerExecutor를 사용합니다.
     * 전략이 없으면 AI 분석 먼저 수행하도록 안내합니다.
     */
    @PostMapping("/games/{id}/crawl")
    public ResponseEntity<Map<String, String>> triggerCrawl(@PathVariable Long id) {
        Optional<Game> gameOpt = gameRepository.findById(id);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOpt.get();
        Optional<CrawlerStrategy> strategyOpt =
                strategyRepository.findTopByGameIdOrderByVersionDesc(id);

        if (strategyOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "AI 분석 후 전략을 먼저 생성하세요."
            ));
        }

        CrawlerStrategy strategy = strategyOpt.get();
        crawlerScheduler.runGenericAsync(id, game.getUrl(), strategy.getStrategyJson());
        log.info("GenericCrawlerExecutor 크롤링 트리거 - gameId={}", id);
        return ResponseEntity.ok(Map.of("message", "크롤링을 시작했습니다."));
    }
}

package com.gamepaper.admin;

import com.gamepaper.crawler.CrawlerScheduler;
import com.gamepaper.crawler.GameCrawler;
import com.gamepaper.domain.game.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminCrawlApiController {

    private final List<GameCrawler> crawlers;
    private final CrawlerScheduler crawlerScheduler;
    private final GameRepository gameRepository;

    /**
     * 특정 게임 즉시 크롤링 트리거.
     * 게임 ID와 일치하는 크롤러를 찾아 동기 실행.
     * (Sprint 4에서 비동기 처리로 전환)
     */
    @PostMapping("/games/{id}/crawl")
    public ResponseEntity<Map<String, String>> triggerCrawl(@PathVariable Long id) {
        if (!gameRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        GameCrawler target = crawlers.stream()
                .filter(c -> c.getGameId().equals(id))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "해당 게임의 크롤러를 찾을 수 없습니다. (Sprint 4에서 GenericCrawlerExecutor 구현 예정)"
            ));
        }

        // 별도 스레드로 크롤러 실행 (블로킹 방지)
        new Thread(() -> crawlerScheduler.runSingle(target)).start();
        log.info("수동 크롤링 트리거 - gameId={}", id);
        return ResponseEntity.ok(Map.of("message", "크롤링을 시작했습니다."));
    }
}

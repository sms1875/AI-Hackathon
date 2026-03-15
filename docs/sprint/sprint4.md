# Sprint 4: AI 범용 크롤러 핵심 구현

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** URL 등록만으로 AI가 파싱 전략을 자동 생성하고, GenericCrawlerExecutor가 해당 전략으로 이미지를 수집하는 범용 크롤러 파이프라인을 구축한다.

**Architecture:** `AnalysisService`가 비동기(@Async)로 HTML 수집 → Claude API 호출 → 전략 저장 파이프라인을 처리한다. `GenericCrawlerExecutor`는 저장된 전략 JSON을 읽어 Selenium/Jsoup을 추상화하여 크롤링을 수행한다. 기존 6개 게임별 크롤러는 GenericCrawlerExecutor 검증 후 제거한다.

**Tech Stack:** Spring Boot 3.x (Java 21), @Async + CompletableFuture, Selenium RemoteWebDriver, Jsoup, Claude API (claude-3-5-sonnet), Spring Data JPA (SQLite), Thymeleaf

**브랜치:** `sprint4` (master에서 분기 완료)

---

## Sprint 3 코드 리뷰 이슈 해소 (선행 작업)

Sprint 3 코드 리뷰에서 제기된 이슈를 Task 1에서 먼저 해소합니다:
- **I-1:** `new Thread()` → `@Async` 교체 (AdminCrawlApiController)
- **I-2:** `RestClient.create()` → `RestClient.Builder` 빈 재사용 (ClaudeApiClient)
- **I-3:** 비활성화 상태를 FAILED로 처리 → `GameStatus.INACTIVE` 추가 (AdminGameController)

---

## Task 1: 코드 리뷰 이슈 해소 + @Async 인프라 설정

**커밋 단위:** Sprint 3 코드 리뷰 이슈 3건 일괄 수정

**Files:**
- Modify: `server/src/main/java/com/gamepaper/config/AppConfig.java`
- Modify: `server/src/main/java/com/gamepaper/domain/game/GameStatus.java`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminGameController.java`
- Modify: `server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java`
- Modify: `server/src/main/java/com/gamepaper/GamepaperApplication.java`

### Step 1: GameStatus에 INACTIVE 추가

`server/src/main/java/com/gamepaper/domain/game/GameStatus.java`를 수정합니다:

```java
package com.gamepaper.domain.game;

public enum GameStatus {
    ACTIVE,
    UPDATING,
    FAILED,
    INACTIVE  // 관리자가 명시적으로 비활성화한 상태 (I-3 해소)
}
```

### Step 2: AppConfig에 @Async 스레드풀 추가

`server/src/main/java/com/gamepaper/config/AppConfig.java`를 수정합니다:

```java
package com.gamepaper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync  // @Async 활성화
public class AppConfig {

    /**
     * 크롤러 스케줄러용 스레드풀
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("crawler-scheduler-");
        return scheduler;
    }

    /**
     * @Async 비동기 작업용 스레드풀 (I-1 해소: new Thread() 대체)
     * AI 분석 및 크롤링 비동기 실행에 사용
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
}
```

### Step 3: ClaudeApiClient에 RestClient.Builder 주입 (I-2 해소)

`server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java`의 `callApi()` 메서드에서 `RestClient.create()` → 생성자 주입으로 교체합니다:

```java
package com.gamepaper.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gamepaper.claude.dto.AnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Claude API 클라이언트.
 * Spring Boot 3.2의 RestClient를 사용합니다.
 * RestClient.Builder 빈 재사용으로 커넥션 풀 공유 (I-2 해소).
 */
@Slf4j
@Component
public class ClaudeApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.model:claude-3-5-sonnet-20241022}")
    private String model;

    @Value("${claude.max-tokens:2048}")
    private int maxTokens;

    private final CrawlerStrategyParser parser;
    private final RestClient restClient;  // 빈 재사용 (I-2 해소)

    public ClaudeApiClient(CrawlerStrategyParser parser, RestClient.Builder restClientBuilder) {
        this.parser = parser;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 주어진 HTML 소스를 분석하여 파싱 전략 JSON을 생성합니다.
     */
    public AnalyzeResponse analyzeHtml(String pageHtml, String pageUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        String prompt = buildPrompt(pageHtml, pageUrl);
        String responseText = callApi(prompt);
        log.debug("Claude API 응답 수신 - 길이={}", responseText.length());

        JsonNode strategyNode = parser.extractJson(responseText);
        return new AnalyzeResponse(strategyNode, MAPPER.valueToTree(strategyNode).toString());
    }

    private String buildPrompt(String html, String url) {
        String cleanedHtml = html
                .replaceAll("(?s)<script[^>]*>.*?</script>", "")
                .replaceAll("(?s)<style[^>]*>.*?</style>", "");
        if (cleanedHtml.length() > 8000) {
            cleanedHtml = cleanedHtml.substring(0, 8000) + "\n... (이하 생략)";
        }

        return """
            당신은 웹 크롤링 전략 전문가입니다.
            아래 HTML을 분석하여 이미지를 수집하기 위한 파싱 전략 JSON을 생성해주세요.

            대상 URL: %s

            HTML:
            %s

            다음 JSON 스키마를 반드시 준수하세요:
            {
              "imageSelector": "이미지를 선택하는 CSS 셀렉터",
              "imageAttribute": "이미지 URL을 추출할 속성 (src, data-src, href 중 하나)",
              "paginationType": "none | button_click | scroll | url_pattern",
              "nextButtonSelector": "다음 페이지 버튼 셀렉터 (button_click일 때)",
              "urlPattern": "페이지 URL 패턴 (url_pattern일 때, {page}를 페이지 번호로)",
              "maxPages": 최대 수집 페이지 수 (숫자),
              "waitMs": 페이지 로딩 대기 시간 ms (숫자),
              "preActions": [
                {"action": "click", "selector": "닫기 버튼 셀렉터"}
              ],
              "stopCondition": "duplicate_count:10 (중복 이미지 N개 연속 시 중단, 선택)"
            }

            JSON만 출력하세요 (```json 블록으로 감싸도 됩니다).
            """.formatted(url, cleanedHtml);
    }

    private String callApi(String prompt) {
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);

        ArrayNode messages = MAPPER.createArrayNode();
        ObjectNode userMessage = MAPPER.createObjectNode();
        userMessage.put("role", "user");

        ArrayNode content = MAPPER.createArrayNode();
        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);

        userMessage.set("content", content);
        messages.add(userMessage);
        requestBody.set("messages", messages);

        String responseBody = restClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = MAPPER.readTree(responseBody);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}
```

### Step 4: AdminGameController toggle-status 수정 (I-3 해소)

`server/src/main/java/com/gamepaper/admin/AdminGameController.java`의 `toggleStatus()` 메서드를 수정합니다:

```java
// 게임 상태 토글 (활성화/비활성화)
@PostMapping("/{id}/toggle-status")
public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
    gameRepository.findById(id).ifPresent(game -> {
        if (game.getStatus() == GameStatus.ACTIVE) {
            game.setStatus(GameStatus.INACTIVE);  // FAILED 대신 INACTIVE 사용 (I-3 해소)
        } else {
            game.setStatus(GameStatus.ACTIVE);
        }
        gameRepository.save(game);
    });
    ra.addFlashAttribute("message", "게임 상태가 변경되었습니다.");
    return "redirect:/admin/games";
}
```

### Step 5: AdminCrawlApiController @Async 교체 (I-1 해소)

`server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java`의 `new Thread()` 부분을 교체합니다.

먼저 `CrawlerScheduler`에 `@Async` 메서드를 추가합니다:

`server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`에 비동기 메서드 추가:

```java
/**
 * 비동기 단일 크롤러 실행 (I-1 해소: new Thread() 대체)
 */
@Async("asyncExecutor")
public void runSingleAsync(GameCrawler crawler) {
    runSingle(crawler);
}
```

`AdminCrawlApiController.java`의 크롤링 트리거 부분:

```java
// new Thread(() -> crawlerScheduler.runSingle(target)).start();  // 기존 코드 제거
crawlerScheduler.runSingleAsync(target);  // @Async로 교체
```

### Step 6: @EnableAsync 활성화 확인 후 빌드 테스트

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 7: 커밋

```bash
git add server/src/main/java/com/gamepaper/config/AppConfig.java
git add server/src/main/java/com/gamepaper/domain/game/GameStatus.java
git add server/src/main/java/com/gamepaper/admin/AdminGameController.java
git add server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java
git add server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java
git add server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java
git commit -m "refactor: Sprint 3 코드 리뷰 이슈 해소 (I-1 @Async, I-2 RestClient.Builder, I-3 INACTIVE)"
```

---

## Task 2: AnalysisStatus 엔티티 + AnalysisService (비동기 AI 분석 파이프라인)

**커밋 단위:** AI 분석 파이프라인 핵심 로직

**Files:**
- Create: `server/src/main/java/com/gamepaper/domain/game/AnalysisStatus.java`
- Modify: `server/src/main/java/com/gamepaper/domain/game/Game.java`
- Create: `server/src/main/java/com/gamepaper/claude/AnalysisService.java`

### Step 1: AnalysisStatus 열거형 생성

```java
// server/src/main/java/com/gamepaper/domain/game/AnalysisStatus.java
package com.gamepaper.domain.game;

/**
 * AI 파싱 전략 분석 상태.
 * PENDING → ANALYZING → COMPLETED | FAILED
 */
public enum AnalysisStatus {
    PENDING,    // 분석 대기 중
    ANALYZING,  // AI 분석 진행 중 (Selenium HTML 수집 + Claude API 호출)
    COMPLETED,  // 분석 완료, CrawlerStrategy 저장됨
    FAILED      // 분석 실패 (HTML 수집 실패 또는 Claude API 오류)
}
```

### Step 2: Game 엔티티에 분석 상태 필드 추가

`server/src/main/java/com/gamepaper/domain/game/Game.java`에 필드 추가:

```java
@Enumerated(EnumType.STRING)
@Column(name = "analysis_status", length = 20)
private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

@Column(name = "analysis_error", length = 500)
private String analysisError;
```

완성된 Game.java:

```java
package com.gamepaper.domain.game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GameStatus status = GameStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Column(name = "analysis_error", length = 500)
    private String analysisError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_crawled_at")
    private LocalDateTime lastCrawledAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Game(String name, String url) {
        this.name = name;
        this.url = url;
        this.status = GameStatus.ACTIVE;
        this.analysisStatus = AnalysisStatus.PENDING;
    }
}
```

### Step 3: AnalysisService 생성

```java
// server/src/main/java/com/gamepaper/claude/AnalysisService.java
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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(demoJson);
            return new AnalyzeResponse(node, demoJson.trim());
        } catch (Exception e) {
            throw new RuntimeException("데모 전략 생성 실패", e);
        }
    }
}
```

### Step 4: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 5: 커밋

```bash
git add server/src/main/java/com/gamepaper/domain/game/AnalysisStatus.java
git add server/src/main/java/com/gamepaper/domain/game/Game.java
git add server/src/main/java/com/gamepaper/claude/AnalysisService.java
git commit -m "feat: AnalysisStatus 열거형 및 비동기 AnalysisService 구현 (Task 2)"
```

---

## Task 3: AI 분석 API 엔드포인트 + 상태 조회 API

**커밋 단위:** AI 분석 트리거 및 상태 polling API

**Files:**
- Modify: `server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminGameController.java`

### Step 1: AdminAnalyzeApiController 재설계

기존 동기 방식 `/admin/api/analyze` → 비동기 `POST /admin/games/{id}/analyze` + `GET /admin/games/{id}/analyze/status`로 재설계합니다.

```java
// server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java
package com.gamepaper.admin;

import com.gamepaper.claude.AnalysisService;
import com.gamepaper.domain.game.AnalysisStatus;
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

        // gameId가 있으면 AnalysisService 위임
        if (request.getGameId() != null) {
            analysisService.analyzeAsync(request.getGameId());
            return ResponseEntity.accepted().body(Map.of(
                    "message", "분석을 시작했습니다.",
                    "statusUrl", "/admin/games/" + request.getGameId() + "/analyze/status"
            ));
        }

        // gameId 없이 URL만 있는 경우 (게임 등록 전 미리보기) — 동기 처리 유지
        try {
            String html = new com.gamepaper.claude.HtmlFetcher().fetch(request.getUrl());
            // HtmlFetcher를 직접 생성하지 말고 주입받아야 함 → 아래 방식 사용
            return ResponseEntity.ok(Map.of("message", "gameId를 포함하여 요청하세요."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
```

**주의:** `/admin/api/analyze` 엔드포인트는 게임 등록 화면(game-new.html)의 "AI 분석 시작" 버튼에서 사용합니다. 게임 미등록 상태에서 미리보기 요청은 HtmlFetcher + ClaudeApiClient를 직접 주입받아 처리합니다. 아래와 같이 수정하세요:

```java
// AdminAnalyzeApiController.java 상단 필드 추가
private final com.gamepaper.claude.ClaudeApiClient claudeApiClient;
private final com.gamepaper.claude.HtmlFetcher htmlFetcher;

// /admin/api/analyze 전체 수정
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

private java.util.Map<String, Object> buildDemoStrategy() {
    return java.util.Map.of(
            "imageSelector", ".wallpaper-list img, .wallpaper img",
            "imageAttribute", "src",
            "paginationType", "button_click",
            "nextButtonSelector", ".pagination .next",
            "maxPages", 5,
            "waitMs", 2000,
            "preActions", java.util.List.of()
    );
}
```

### Step 2: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java
git commit -m "feat: AI 분석 트리거 및 상태 polling API 구현 (Task 3)"
```

---

## Task 4: 프론트엔드 AI 분석 polling 연결

**커밋 단위:** 게임 등록/상세 화면 polling UI

**Files:**
- Modify: `server/src/main/resources/templates/admin/game-new.html`
- Modify: `server/src/main/resources/templates/admin/game-detail.html`
- Modify: `server/src/main/resources/templates/admin/game-list.html`

### Step 1: game-new.html polling 스크립트 확인 및 수정

`server/src/main/resources/templates/admin/game-new.html`에서 AI 분석 관련 JavaScript를 확인합니다. 기존 `/admin/api/analyze` 호출 방식을 `POST /admin/games/{id}/analyze` + polling으로 교체합니다.

게임 등록 폼 흐름:
1. 사용자가 게임명 + URL 입력
2. "AI 분석 시작" 버튼 클릭 → 게임 먼저 저장(POST /admin/games/new) → 응답으로 게임 ID 수신
3. `/admin/games/{id}/analyze` 호출 → 202 반환
4. 2초 간격으로 `/admin/games/{id}/analyze/status` polling
5. COMPLETED 시 전략 JSON 미리보기 표시

`game-new.html`의 JavaScript 섹션에 다음 코드를 추가/교체합니다:

```html
<script th:inline="javascript">
    // AI 분석 polling 로직
    let pollingTimer = null;

    // 1단계: 게임 저장 후 분석 시작
    function startAnalysisWithSave() {
        const name = document.getElementById('gameName').value.trim();
        const url = document.getElementById('gameUrl').value.trim();

        if (!name || !url) {
            showStatus('게임명과 URL을 모두 입력해주세요.', 'error');
            return;
        }

        showStatus('게임 정보 저장 중...', 'info');

        // 게임 먼저 저장
        fetch('/admin/games/new', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `name=${encodeURIComponent(name)}&url=${encodeURIComponent(url)}&analyzeOnly=true`
        })
        .then(response => response.json())
        .then(data => {
            if (data.gameId) {
                document.getElementById('savedGameId').value = data.gameId;
                startPolling(data.gameId);
            } else {
                showStatus('게임 저장 실패: ' + (data.error || '알 수 없는 오류'), 'error');
            }
        })
        .catch(err => showStatus('오류: ' + err.message, 'error'));
    }

    // 2단계: 분석 시작 + polling
    function startPolling(gameId) {
        showStatus('AI 분석 시작 중...', 'info');

        fetch(`/admin/games/${gameId}/analyze`, {method: 'POST'})
        .then(() => {
            showStatus('AI가 파싱 전략을 생성하고 있습니다... (최대 60초)', 'info');
            pollingTimer = setInterval(() => checkStatus(gameId), 2000);
        })
        .catch(err => showStatus('분석 시작 실패: ' + err.message, 'error'));
    }

    // 상태 polling
    function checkStatus(gameId) {
        fetch(`/admin/games/${gameId}/analyze/status`)
        .then(response => response.json())
        .then(data => {
            if (data.status === 'COMPLETED') {
                clearInterval(pollingTimer);
                showStatus('전략 생성 완료!', 'success');
                showStrategyPreview(data.strategy);
            } else if (data.status === 'FAILED') {
                clearInterval(pollingTimer);
                showStatus('분석 실패: ' + data.error, 'error');
            } else {
                showStatus(data.message || 'AI 분석 중...', 'info');
            }
        })
        .catch(err => {
            clearInterval(pollingTimer);
            showStatus('상태 확인 실패: ' + err.message, 'error');
        });
    }

    function showStatus(message, type) {
        const el = document.getElementById('analysisStatus');
        el.textContent = message;
        el.className = 'alert alert-' +
            (type === 'error' ? 'danger' : type === 'success' ? 'success' : 'info');
        el.style.display = 'block';
    }

    function showStrategyPreview(strategyJson) {
        const preview = document.getElementById('strategyPreview');
        const textarea = document.getElementById('strategyJson');
        try {
            const parsed = typeof strategyJson === 'string'
                ? JSON.parse(strategyJson) : strategyJson;
            textarea.value = JSON.stringify(parsed, null, 2);
        } catch(e) {
            textarea.value = typeof strategyJson === 'string'
                ? strategyJson : JSON.stringify(strategyJson, null, 2);
        }
        preview.style.display = 'block';
    }
</script>
```

게임 등록 폼 HTML 수정 (AI 분석 버튼 + 상태 표시 영역):

```html
<!-- 숨겨진 게임 ID 필드 (저장 후 채워짐) -->
<input type="hidden" id="savedGameId" name="savedGameId" value="">

<!-- AI 분석 상태 표시 -->
<div id="analysisStatus" class="alert" style="display:none;"></div>

<!-- AI 분석 버튼 -->
<button type="button" class="btn btn-primary" onclick="startAnalysisWithSave()">
    AI 분석 시작
</button>

<!-- 전략 미리보기 (분석 완료 후 표시) -->
<div id="strategyPreview" style="display:none;">
    <h6>파싱 전략 미리보기</h6>
    <textarea id="strategyJson" class="form-control" rows="12" readonly
              style="font-family: monospace; font-size: 0.85rem;"></textarea>
    <!-- 수동 수정 토글 -->
    <button type="button" class="btn btn-sm btn-outline-secondary mt-2"
            onclick="document.getElementById('strategyJson').removeAttribute('readonly')">
        수동 수정
    </button>
</div>
```

**AdminGameController 수정:** `POST /admin/games/new`에서 `analyzeOnly=true` 파라미터를 처리하여 JSON으로 gameId 반환:

```java
// 게임 등록 처리 (analyzeOnly 파라미터 지원)
@PostMapping("/new")
@ResponseBody  // JSON 반환을 위해 추가 (analyzeOnly 케이스)
public Object createGame(
        @RequestParam String name,
        @RequestParam String url,
        @RequestParam(required = false, defaultValue = "false") boolean analyzeOnly,
        RedirectAttributes ra) {

    if (name.isBlank() || url.isBlank()) {
        if (analyzeOnly) {
            return ResponseEntity.badRequest().body(Map.of("error", "게임명과 URL을 모두 입력해주세요."));
        }
        ra.addFlashAttribute("error", "게임명과 URL을 모두 입력해주세요.");
        return "redirect:/admin/games/new";
    }

    Game game = new Game(name, url);
    gameRepository.save(game);

    if (analyzeOnly) {
        return Map.of("gameId", game.getId(), "name", game.getName());
    }

    ra.addFlashAttribute("message", "게임 '" + name + "'이(가) 등록되었습니다.");
    return "redirect:/admin/games/" + game.getId();
}
```

**주의:** Spring MVC에서 `@ResponseBody`와 `redirect:` 문자열 반환을 혼용할 수 없습니다. `analyzeOnly` 케이스를 처리하려면 `ResponseEntity<?>` 반환 타입으로 통일해야 합니다:

```java
@PostMapping("/new")
public ResponseEntity<?> createGame(
        @RequestParam String name,
        @RequestParam String url,
        @RequestParam(required = false, defaultValue = "false") boolean analyzeOnly) {

    if (name.isBlank() || url.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "게임명과 URL을 모두 입력해주세요."));
    }

    Game game = new Game(name, url);
    gameRepository.save(game);

    if (analyzeOnly) {
        // AJAX 요청: JSON으로 gameId 반환
        return ResponseEntity.ok(Map.of("gameId", game.getId(), "name", game.getName()));
    }

    // 일반 폼 제출: 리다이렉트
    return ResponseEntity.status(302)
            .location(java.net.URI.create("/admin/games/" + game.getId()))
            .build();
}
```

### Step 2: game-detail.html "재분석" 버튼 추가

`server/src/main/resources/templates/admin/game-detail.html`의 전략 탭에 재분석 버튼과 상태 표시를 추가합니다:

```html
<!-- 전략 탭 상단 -->
<div class="d-flex justify-content-between align-items-center mb-3">
    <h6>현재 파싱 전략</h6>
    <button type="button" class="btn btn-warning btn-sm"
            onclick="reanalyze([[${game.id}]])">
        재분석
    </button>
</div>
<div id="reanalyzeStatus" class="alert" style="display:none;"></div>

<script th:inline="javascript">
    function reanalyze(gameId) {
        if (!confirm('재분석 시 새 전략이 생성됩니다. 기존 전략은 이력에 보존됩니다. 계속하시겠습니까?')) return;

        document.getElementById('reanalyzeStatus').textContent = 'AI 재분석 시작...';
        document.getElementById('reanalyzeStatus').className = 'alert alert-info';
        document.getElementById('reanalyzeStatus').style.display = 'block';

        fetch(`/admin/games/${gameId}/analyze`, {method: 'POST'})
        .then(() => {
            const timer = setInterval(() => {
                fetch(`/admin/games/${gameId}/analyze/status`)
                .then(r => r.json())
                .then(data => {
                    if (data.status === 'COMPLETED') {
                        clearInterval(timer);
                        document.getElementById('reanalyzeStatus').textContent =
                            '재분석 완료! 페이지를 새로고침합니다.';
                        document.getElementById('reanalyzeStatus').className = 'alert alert-success';
                        setTimeout(() => location.reload(), 1500);
                    } else if (data.status === 'FAILED') {
                        clearInterval(timer);
                        document.getElementById('reanalyzeStatus').textContent =
                            '재분석 실패: ' + data.error;
                        document.getElementById('reanalyzeStatus').className = 'alert alert-danger';
                    }
                });
            }, 2000);
        });
    }
</script>
```

### Step 3: game-list.html 분석 상태 뱃지 추가

게임 목록 테이블에 `analysisStatus` 뱃지를 추가합니다:

```html
<!-- 게임 목록 테이블 - 분석 상태 컬럼 추가 -->
<td>
    <span th:switch="${game.analysisStatus?.name()}">
        <span th:case="'COMPLETED'" class="badge bg-success">분석완료</span>
        <span th:case="'ANALYZING'" class="badge bg-warning text-dark">분석중</span>
        <span th:case="'FAILED'" class="badge bg-danger">분석실패</span>
        <span th:case="*" class="badge bg-secondary">미분석</span>
    </span>
</td>
```

### Step 4: 빌드 + 서버 기동 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 5: 커밋

```bash
git add server/src/main/resources/templates/admin/
git add server/src/main/java/com/gamepaper/admin/AdminGameController.java
git commit -m "feat: 프론트엔드 AI 분석 polling UI 연결 (Task 4)"
```

---

## Task 5: GenericCrawlerExecutor 구현

**커밋 단위:** 범용 크롤러 실행기 핵심 구현

**Files:**
- Create: `server/src/main/java/com/gamepaper/crawler/generic/StrategyDto.java`
- Create: `server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java`

### Step 1: StrategyDto 생성 (전략 JSON 역직렬화용)

```java
// server/src/main/java/com/gamepaper/crawler/generic/StrategyDto.java
package com.gamepaper.crawler.generic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * CrawlerStrategy JSON을 자바 객체로 역직렬화하는 DTO.
 * 알 수 없는 필드는 무시 (스키마 버전 확장 대비).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyDto {

    /** 이미지를 선택하는 CSS 셀렉터 */
    private String imageSelector;

    /** 이미지 URL을 추출할 속성 (src, data-src, href) */
    private String imageAttribute = "src";

    /** 페이지네이션 타입: none | button_click | scroll | url_pattern */
    private String paginationType = "none";

    /** 다음 페이지 버튼 셀렉터 (button_click일 때) */
    private String nextButtonSelector;

    /** 페이지 URL 패턴 (url_pattern일 때, {page}를 페이지 번호로) */
    private String urlPattern;

    /** 최대 수집 페이지 수 (기본 5) */
    private int maxPages = 5;

    /** 페이지 로딩 대기 시간 ms (기본 2000) */
    private int waitMs = 2000;

    /** 실행기 타임아웃 초 (기본 30) */
    private int timeoutSeconds = 30;

    /** 페이지 접속 전 사전 동작 목록 */
    private List<Map<String, String>> preActions;

    /**
     * 크롤링 중단 조건.
     * 예: "duplicate_count:10" - 중복 이미지 10개 연속 수집 시 중단
     */
    private String stopCondition;
}
```

### Step 2: GenericCrawlerExecutor 구현

```java
// server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java
package com.gamepaper.crawler.generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CrawlerStrategy JSON을 읽어 크롤링을 수행하는 범용 실행기.
 *
 * 지원 페이지네이션:
 * - none: 단일 페이지 수집
 * - button_click: "다음 페이지" 버튼 클릭
 * - scroll: 페이지 하단으로 스크롤 (무한 스크롤)
 * - url_pattern: URL 패턴({page})으로 페이지 순회
 *
 * 사용 후 WebDriver 세션은 반드시 종료됩니다 (finally 블록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericCrawlerExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StorageService storageService;
    private final WallpaperRepository wallpaperRepository;
    private final ImageProcessor imageProcessor;

    @Value("${selenium.hub-url:http://localhost:4444}")
    private String seleniumHubUrl;

    /**
     * 전략 JSON 문자열을 파싱하여 크롤링을 실행합니다.
     *
     * @param gameId      크롤링 대상 게임 ID
     * @param gameUrl     크롤링 시작 URL
     * @param strategyJson 파싱 전략 JSON 문자열
     * @return 수집 결과 (성공/실패, 수집 개수)
     */
    public CrawlResult execute(Long gameId, String gameUrl, String strategyJson) {
        StrategyDto strategy;
        try {
            strategy = MAPPER.readValue(strategyJson, StrategyDto.class);
        } catch (Exception e) {
            log.error("전략 JSON 파싱 실패 - gameId={}", gameId, e);
            return CrawlResult.failure("전략 JSON 파싱 실패: " + e.getMessage());
        }

        if (strategy.getImageSelector() == null || strategy.getImageSelector().isBlank()) {
            return CrawlResult.failure("imageSelector가 비어 있습니다.");
        }

        log.info("GenericCrawlerExecutor 실행 - gameId={}, paginationType={}",
                gameId, strategy.getPaginationType());

        WebDriver driver = null;
        try {
            driver = createDriver(strategy.getTimeoutSeconds());
            return crawl(driver, gameId, gameUrl, strategy);
        } catch (Exception e) {
            log.error("크롤링 실행 오류 - gameId={}", gameId, e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            // 세션 반드시 종료 (메모리 누수 방지)
            quitDriver(driver);
        }
    }

    private CrawlResult crawl(WebDriver driver, Long gameId,
                               String gameUrl, StrategyDto strategy) {
        int totalCount = 0;
        int consecutiveDuplicates = 0;
        int duplicateThreshold = parseDuplicateThreshold(strategy.getStopCondition());

        // 사전 동작 실행 (팝업 닫기 등)
        driver.get(gameUrl);
        sleep(strategy.getWaitMs());
        executePreActions(driver, strategy.getPreActions());

        String pagination = strategy.getPaginationType() != null
                ? strategy.getPaginationType() : "none";

        switch (pagination) {
            case "scroll" -> {
                for (int i = 0; i < strategy.getMaxPages(); i++) {
                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    if (newCount == 0) consecutiveDuplicates++;
                    else consecutiveDuplicates = 0;

                    if (duplicateThreshold > 0 && consecutiveDuplicates >= duplicateThreshold) {
                        log.info("중단 조건 충족 - consecutiveDuplicates={}", consecutiveDuplicates);
                        break;
                    }

                    ((JavascriptExecutor) driver)
                            .executeScript("window.scrollTo(0, document.body.scrollHeight)");
                    sleep(strategy.getWaitMs());
                }
            }
            case "button_click" -> {
                for (int page = 0; page < strategy.getMaxPages(); page++) {
                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    // 다음 버튼 찾기
                    if (strategy.getNextButtonSelector() == null) break;
                    try {
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        WebElement nextBtn = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.cssSelector(strategy.getNextButtonSelector())));
                        nextBtn.click();
                        sleep(strategy.getWaitMs());
                    } catch (Exception e) {
                        log.debug("다음 버튼 없음 - 크롤링 종료 (page={})", page);
                        break;
                    }
                }
            }
            case "url_pattern" -> {
                if (strategy.getUrlPattern() == null) break;
                for (int page = 1; page <= strategy.getMaxPages(); page++) {
                    String url = strategy.getUrlPattern().replace("{page}", String.valueOf(page));
                    driver.get(url);
                    sleep(strategy.getWaitMs());

                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    if (newCount == 0) {
                        log.debug("이미지 없음 - 크롤링 종료 (page={})", page);
                        break;
                    }
                }
            }
            default -> {
                // none: 단일 페이지
                totalCount = collectImages(driver, gameId, strategy);
            }
        }

        log.info("GenericCrawlerExecutor 완료 - gameId={}, 총 수집={}", gameId, totalCount);
        return CrawlResult.success(totalCount);
    }

    /**
     * 현재 페이지에서 이미지를 수집하여 저장합니다.
     *
     * @return 신규 저장된 이미지 수
     */
    private int collectImages(WebDriver driver, Long gameId, StrategyDto strategy) {
        int count = 0;
        try {
            List<WebElement> elements = driver.findElements(
                    By.cssSelector(strategy.getImageSelector()));

            List<String> urls = new ArrayList<>();
            for (WebElement el : elements) {
                String attr = strategy.getImageAttribute() != null
                        ? strategy.getImageAttribute() : "src";
                String url = el.getAttribute(attr);
                if (url == null || url.isBlank()) continue;
                if (!urls.contains(url)) urls.add(url);
            }

            for (String imageUrl : urls) {
                if (downloadAndSave(gameId, imageUrl)) {
                    count++;
                }
                sleep(500);  // 요청 간 딜레이
            }
        } catch (Exception e) {
            log.warn("이미지 수집 중 오류 - gameId={}, 오류={}", gameId, e.getMessage());
        }
        return count;
    }

    /**
     * 사전 동작(preActions) 실행.
     * 지원 action: click, wait
     */
    private void executePreActions(WebDriver driver, List<Map<String, String>> preActions) {
        if (preActions == null || preActions.isEmpty()) return;

        for (Map<String, String> action : preActions) {
            String type = action.getOrDefault("action", "");
            String selector = action.get("selector");

            try {
                switch (type) {
                    case "click" -> {
                        if (selector != null) {
                            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                            WebElement el = wait.until(
                                    ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                            el.click();
                            log.debug("preAction click 실행 - selector={}", selector);
                        }
                    }
                    case "wait" -> {
                        int ms = Integer.parseInt(action.getOrDefault("ms", "1000"));
                        sleep(ms);
                    }
                    default -> log.warn("알 수 없는 preAction 타입: {}", type);
                }
            } catch (Exception e) {
                // preAction 실패는 무시하고 계속 진행 (팝업이 없는 경우 등)
                log.debug("preAction 실행 실패 (무시) - action={}, 오류={}", type, e.getMessage());
            }
        }
    }

    /**
     * stopCondition 파싱.
     * "duplicate_count:10" → 10 반환
     */
    private int parseDuplicateThreshold(String stopCondition) {
        if (stopCondition == null || !stopCondition.startsWith("duplicate_count:")) return 0;
        try {
            return Integer.parseInt(stopCondition.split(":")[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 이미지 URL 다운로드 후 저장. AbstractGameCrawler.downloadAndSave()와 동일한 로직.
     */
    private boolean downloadAndSave(Long gameId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        String ext = imageProcessor.extractExtension(imageUrl);
        String urlHash = String.valueOf(Math.abs(imageUrl.hashCode()));
        String fileName = urlHash + "." + ext;

        if (wallpaperRepository.existsByGameIdAndFileName(gameId, fileName)) {
            log.debug("중복 이미지 건너뜀 - gameId={}, fileName={}", gameId, fileName);
            return false;
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes.length == 0) return false;

            com.gamepaper.crawler.image.ImageMetadata metadata =
                    imageProcessor.process(imageBytes, ext);
            String savedUrl = storageService.upload(gameId, fileName, imageBytes);

            com.gamepaper.domain.wallpaper.Wallpaper wallpaper =
                    new com.gamepaper.domain.wallpaper.Wallpaper(gameId, fileName, savedUrl);
            wallpaper.setWidth(metadata.getWidth());
            wallpaper.setHeight(metadata.getHeight());
            wallpaper.setBlurHash(metadata.getBlurHash());

            wallpaperRepository.save(wallpaper);
            log.debug("이미지 저장 완료 - gameId={}, url={}", gameId, imageUrl);
            return true;

        } catch (Exception e) {
            log.warn("이미지 저장 실패 - url={}, 오류={}", imageUrl, e.getMessage());
            return false;
        }
    }

    private byte[] downloadImage(String imageUrl)
            throws java.io.IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "Mozilla/5.0 (compatible; GamePaper-Crawler/1.0)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        java.net.http.HttpResponse<byte[]> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.warn("이미지 다운로드 실패 - status={}, url={}", response.statusCode(), imageUrl);
            return new byte[0];
        }
        return response.body();
    }

    private WebDriver createDriver(int timeoutSeconds) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );
        try {
            RemoteWebDriver driver = new RemoteWebDriver(
                    URI.create(seleniumHubUrl + "/wd/hub").toURL(), options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            return driver;
        } catch (Exception e) {
            throw new RuntimeException("Selenium 드라이버 생성 실패: " + seleniumHubUrl, e);
        }
    }

    private void quitDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("WebDriver 종료 실패: {}", e.getMessage());
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### Step 3: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 4: 커밋

```bash
git add server/src/main/java/com/gamepaper/crawler/generic/
git commit -m "feat: GenericCrawlerExecutor 구현 - 전략 JSON 기반 범용 크롤러 (Task 5)"
```

---

## Task 6: AdminCrawlApiController — GenericCrawlerExecutor 연결

**커밋 단위:** 관리자 크롤링 API를 GenericCrawlerExecutor로 교체

**Files:**
- Modify: `server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java`
- Modify: `server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`

### Step 1: AdminCrawlApiController 수정

크롤링 트리거 시 먼저 `CrawlerStrategy`(GenericCrawlerExecutor용)를 조회하고, 없으면 기존 GameCrawler 목록에서 fallback합니다.

```java
// server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java
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
```

### Step 2: CrawlerScheduler에 GenericCrawlerExecutor 비동기 메서드 추가

```java
// CrawlerScheduler.java에 추가 (GenericCrawlerExecutor 주입 및 메서드 추가)

// 필드 추가
private final GenericCrawlerExecutor genericCrawlerExecutor;

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
```

### Step 3: 연속 3회 실패 자동 FAILED 전환 로직 추가 (S4-4 선행)

`CrawlerScheduler`에 실패 카운트 확인 로직:

```java
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
```

`CrawlingLogRepository`에 쿼리 메서드 추가:

```java
// server/src/main/java/com/gamepaper/domain/crawler/CrawlingLogRepository.java에 추가
List<CrawlingLog> findTop3ByGameIdOrderByStartedAtDesc(Long gameId);
```

### Step 4: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 5: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java
git add server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java
git add server/src/main/java/com/gamepaper/domain/crawler/CrawlingLogRepository.java
git commit -m "feat: AdminCrawlApiController GenericCrawlerExecutor 연결 및 3회 실패 처리 (Task 6)"
```

---

## Task 7: 기존 6개 게임 DB 등록 데이터 SQL + 마이그레이션 확인

**커밋 단위:** 기존 6개 게임 초기 데이터 등록

**Files:**
- Create: `server/src/main/resources/db/migration/V2__insert_initial_games.sql` (또는 DataInitializer 클래스)
- Modify: `server/src/main/java/com/gamepaper/config/DatabaseConfig.java`

### Step 1: 기존 6개 게임 URL 확인

```
1. 원신 (GenshinCrawler)     → https://www.hoyolab.com/wallpaper
2. 메이플스토리 (MapleStoryCrawler) → 크롤러 파일에서 TARGET_URL 확인 필요
3. 마비노기 (MabinogiCrawler) → 크롤러 파일에서 TARGET_URL 확인 필요
4. 니케 (NikkeCrawler)        → 크롤러 파일에서 TARGET_URL 확인 필요
5. 파판14 (FinalFantasyXIVCrawler) → 크롤러 파일에서 TARGET_URL 확인 필요
6. 검은사막 (BlackDesertCrawler) → https://www.kr.playblackdesert.com/ko-KR/About/WallPaper
```

각 크롤러 파일에서 `TARGET_URL` 상수를 확인한 후 SQL을 작성합니다.

### Step 2: DataInitializer 구현 (SQL 마이그레이션 대신 코드 방식)

JPA auto-ddl 환경에서는 Flyway SQL보다 `CommandLineRunner`를 사용하는 것이 더 안전합니다.

```java
// server/src/main/java/com/gamepaper/config/DataInitializer.java
package com.gamepaper.config;

import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 시작 시 기존 6개 게임 초기 데이터 등록.
 * 이미 같은 URL이 존재하면 건너뜁니다 (멱등성 보장).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final GameRepository gameRepository;

    // 게임명 → URL 매핑 (각 크롤러 클래스의 TARGET_URL 상수와 동일)
    private static final List<String[]> INITIAL_GAMES = List.of(
            new String[]{"원신", "https://www.hoyolab.com/wallpaper"},
            new String[]{"메이플스토리", ""},    // 크롤러에서 URL 확인 후 채우기
            new String[]{"마비노기", ""},        // 크롤러에서 URL 확인 후 채우기
            new String[]{"니케", ""},            // 크롤러에서 URL 확인 후 채우기
            new String[]{"파이널판타지14", ""},  // 크롤러에서 URL 확인 후 채우기
            new String[]{"검은사막", "https://www.kr.playblackdesert.com/ko-KR/About/WallPaper"}
    );

    @Override
    public void run(String... args) {
        for (String[] gameInfo : INITIAL_GAMES) {
            String name = gameInfo[0];
            String url = gameInfo[1];

            if (url == null || url.isBlank()) {
                log.warn("게임 URL 미설정 - 건너뜀: {}", name);
                continue;
            }

            boolean exists = gameRepository.findAll().stream()
                    .anyMatch(g -> g.getUrl().equals(url));

            if (!exists) {
                Game game = new Game(name, url);
                gameRepository.save(game);
                log.info("초기 게임 등록 완료 - name={}, url={}", name, url);
            } else {
                log.debug("게임 이미 존재 - 건너뜀: {}", name);
            }
        }
    }
}
```

**URL 확인 작업:** 각 크롤러 파일의 `TARGET_URL` 상수를 읽어 `INITIAL_GAMES`를 채웁니다.

### Step 3: 나머지 크롤러 URL 확인 후 DataInitializer 완성

```bash
# 각 크롤러의 TARGET_URL 확인
grep -n "TARGET_URL\|BASE_URL" \
  server/src/main/java/com/gamepaper/crawler/selenium/MapleStoryCrawler.java \
  server/src/main/java/com/gamepaper/crawler/selenium/MabinogiCrawler.java \
  server/src/main/java/com/gamepaper/crawler/selenium/NikkeCrawler.java \
  server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java
```

### Step 4: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 5: 커밋

```bash
git add server/src/main/java/com/gamepaper/config/DataInitializer.java
git commit -m "feat: 기존 6개 게임 초기 데이터 자동 등록 DataInitializer (Task 7)"
```

---

## Task 8: CrawlerScheduler 스케줄 — GenericCrawlerExecutor 우선 실행

**커밋 단위:** 스케줄러가 전략 있는 게임을 GenericCrawlerExecutor로 실행

**Files:**
- Modify: `server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`

### Step 1: runAll() 메서드 수정

6시간 주기 스케줄에서 전략이 있는 게임은 GenericCrawlerExecutor, 없는 게임은 기존 크롤러를 사용합니다.

```java
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
```

`CrawlerScheduler`에 `CrawlerStrategyRepository` 의존성 추가:

```java
// CrawlerScheduler.java 필드 추가
private final com.gamepaper.domain.strategy.CrawlerStrategyRepository strategyRepository;
```

### Step 2: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: 커밋

```bash
git add server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java
git commit -m "feat: CrawlerScheduler GenericCrawlerExecutor 우선 실행 전략 (Task 8)"
```

---

## Task 9: 전체 빌드 + 통합 검증

**커밋 단위:** 검증 결과 문서화

**Files:**
- Test via: `./gradlew build`
- Create: `docs/sprint/sprint4/deploy.md`

### Step 1: 전체 빌드

```bash
cd /d/work/AI해커톤/server
./gradlew build
```

Expected: BUILD SUCCESSFUL

### Step 2: Docker 컨테이너 실행 (수동 - deploy.md 참고)

```bash
# 프로젝트 루트에서
docker compose up --build
```

### Step 3: API 기능 검증 (자동)

서버 기동 후 다음 항목을 확인합니다:

```bash
# 게임 목록 조회
curl -s http://localhost:8080/api/games | jq .

# 분석 상태 조회 (게임 ID=1 가정)
curl -s http://localhost:8080/admin/games/1/analyze/status | jq .

# AI 분석 트리거 (게임 ID=1 가정)
curl -s -X POST http://localhost:8080/admin/games/1/analyze | jq .

# 크롤링 트리거 (게임 ID=1, 전략 없는 경우)
curl -s -X POST http://localhost:8080/admin/api/games/1/crawl | jq .
```

### Step 4: deploy.md 생성

```markdown
# Sprint 4 배포 가이드

## 자동 검증 완료 항목
- ✅ Gradle 빌드 성공
- ✅ POST /admin/games/{id}/analyze → 202 반환
- ✅ GET /admin/games/{id}/analyze/status → 상태 JSON 반환
- ✅ POST /admin/api/games/{id}/crawl → 200 반환

## 수동 검증 필요 항목
- ⬜ docker compose up --build — 새 코드 반영 재빌드
- ⬜ 관리자 UI http://localhost:8080/admin 접속 → 게임 목록에 6개 게임 표시 확인
- ⬜ 게임 등록 화면 /admin/games/new → "AI 분석 시작" 버튼 → polling 진행 상태 확인
- ⬜ 게임 상세 화면 → "재분석" 버튼 → 새 버전 전략 생성 확인
- ⬜ 크롤링 트리거 → 로그 탭에서 수집 결과 확인
- ⬜ ANTHROPIC_API_KEY 환경변수 설정 확인 (.env 파일)

## 환경 변수 (필수)
- ANTHROPIC_API_KEY: Claude API 키 (미설정 시 데모 전략 사용)
- selenium.hub-url: Selenium Hub URL (기본: http://localhost:4444)
- crawler.schedule.delay-ms: 크롤링 주기 ms (기본: 21600000 = 6시간)

## 기존 크롤러 제거 타이밍
GenericCrawlerExecutor로 6개 게임 크롤링 결과 검증 후 기존 크롤러 클래스 제거:
- server/src/main/java/com/gamepaper/crawler/selenium/ (GenshinCrawler, MabinogiCrawler, MapleStoryCrawler, NikkeCrawler)
- server/src/main/java/com/gamepaper/crawler/jsoup/ (FinalFantasyXIVCrawler, BlackDesertCrawler)
```

### Step 5: 커밋

```bash
git add docs/sprint/sprint4/deploy.md
git commit -m "docs: Sprint 4 배포 가이드 작성 (Task 9)"
```

---

## Task 10: 기존 크롤러 클래스 제거 (GenericCrawlerExecutor 검증 후)

**커밋 단위:** 기존 게임별 크롤러 제거

**주의:** 이 Task는 Task 9 검증이 완료되어 GenericCrawlerExecutor가 6개 게임 모두 정상 크롤링을 확인한 후에 진행합니다.

**Files:**
- Delete: `server/src/main/java/com/gamepaper/crawler/selenium/GenshinCrawler.java`
- Delete: `server/src/main/java/com/gamepaper/crawler/selenium/MabinogiCrawler.java`
- Delete: `server/src/main/java/com/gamepaper/crawler/selenium/MapleStoryCrawler.java`
- Delete: `server/src/main/java/com/gamepaper/crawler/selenium/NikkeCrawler.java`
- Delete: `server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java`
- Delete: `server/src/main/java/com/gamepaper/crawler/jsoup/BlackDesertCrawler.java`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java` (fallback 제거)

### Step 1: 기존 크롤러 파일 삭제

```bash
rm server/src/main/java/com/gamepaper/crawler/selenium/GenshinCrawler.java
rm server/src/main/java/com/gamepaper/crawler/selenium/MabinogiCrawler.java
rm server/src/main/java/com/gamepaper/crawler/selenium/MapleStoryCrawler.java
rm server/src/main/java/com/gamepaper/crawler/selenium/NikkeCrawler.java
rm server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java
rm server/src/main/java/com/gamepaper/crawler/jsoup/BlackDesertCrawler.java
```

### Step 2: AdminCrawlApiController fallback 코드 정리

기존 `GameCrawler` 목록 의존성 및 fallback 로직 제거:

```java
// AdminCrawlApiController.java - 단순화
@PostMapping("/games/{id}/crawl")
public ResponseEntity<Map<String, String>> triggerCrawl(@PathVariable Long id) {
    Optional<Game> gameOpt = gameRepository.findById(id);
    if (gameOpt.isEmpty()) return ResponseEntity.notFound().build();

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
    return ResponseEntity.ok(Map.of("message", "크롤링을 시작했습니다."));
}
```

### Step 3: CrawlerScheduler fallback 코드 정리

```java
// runAll() 단순화: 기존 크롤러 fallback 블록 제거
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
```

### Step 4: 빌드 확인

```bash
cd /d/work/AI해커톤/server
./gradlew build
```

Expected: BUILD SUCCESSFUL

### Step 5: 커밋

```bash
git add -A
git commit -m "refactor: 기존 게임별 크롤러 클래스 제거 - GenericCrawlerExecutor로 대체 완료 (Task 10)"
```

---

## 완료 기준 (Definition of Done)

- ✅ 게임 URL 등록 → AI가 60초 이내에 파싱 전략 JSON을 생성한다
- ✅ `GET /admin/games/{id}/analyze/status`가 PENDING/ANALYZING/COMPLETED/FAILED 상태를 반환한다
- ✅ 관리자 UI에서 2초 간격 polling으로 분석 진행 상태가 실시간 표시된다
- ✅ GenericCrawlerExecutor가 생성된 전략으로 이미지를 수집한다
- ✅ 기존 6개 게임이 DB에 등록되어 있다
- ✅ 크롤링 연속 3회 실패 시 게임 상태가 FAILED로 전환된다
- ✅ 관리자 UI "재분석" 버튼으로 새 버전 전략이 생성된다
- ✅ Sprint 3 코드 리뷰 이슈 3건(I-1, I-2, I-3)이 해소되었다
- ✅ `GameStatus.INACTIVE`가 추가되어 비활성화 게임이 올바른 상태로 저장된다

---

## 의존성 및 리스크

| 리스크 | 영향 | 완화 방안 |
|--------|------|-----------|
| Claude API 키 미설정 | AI 분석 불가 | 데모 전략 자동 fallback, 개발 환경에서도 동작 |
| Selenium Hub 미기동 | 크롤링 불가 | docker compose up 확인 필요 |
| 전략 JSON imageSelector 부정확 | 이미지 수집 0건 | 관리자 UI 수동 수정 기능 제공 |
| 게임 사이트 HTML 구조 변경 | 기존 전략 무효화 | 재분석 버튼으로 즉시 대응 |
| @Async 순환 의존 오류 | 빌드 실패 | `@EnableAsync`를 `AppConfig`에서 단일 관리 |

---

## 예상 산출물

- `server/src/main/java/com/gamepaper/domain/game/AnalysisStatus.java`
- `server/src/main/java/com/gamepaper/claude/AnalysisService.java`
- `server/src/main/java/com/gamepaper/crawler/generic/StrategyDto.java`
- `server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java`
- `server/src/main/java/com/gamepaper/config/DataInitializer.java`
- 수정: `Game.java`, `GameStatus.java`, `AppConfig.java`, `ClaudeApiClient.java`
- 수정: `AdminAnalyzeApiController.java`, `AdminCrawlApiController.java`, `AdminGameController.java`
- 수정: `CrawlerScheduler.java`, `CrawlingLogRepository.java`
- 수정: 관리자 UI Thymeleaf 템플릿 3개 (`game-new.html`, `game-detail.html`, `game-list.html`)
- `docs/sprint/sprint4/deploy.md`

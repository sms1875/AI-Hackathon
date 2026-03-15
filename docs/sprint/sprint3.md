# Sprint 3: 관리자 UI 프론트엔드 + AI 크롤러 기초 구현 계획

> **상태:** ✅ 완료 (2026-03-15)

## 검증 결과

- [검증 보고서](sprint3/validation-report.md) — 자동/수동 검증 항목, 테스트 22개 전체 통과
- [코드 리뷰](sprint3/code-review.md) — Critical 0, Important 3, Suggestion 4

---

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Thymeleaf 기반 관리자 UI(대시보드, 게임 목록/등록/상세)와 Claude API 연동 기초를 구현하여 브라우저에서 게임을 등록하고 AI 파싱 전략을 생성할 수 있는 MVP 관리자 페이지를 완성한다.

**Architecture:** Spring Boot 내에 `admin` 패키지를 신설하여 Thymeleaf 컨트롤러를 배치한다. 레이아웃은 Thymeleaf Fragment(`layout/base.html`)로 공통화한다. Claude API 호출은 `claude` 패키지의 `ClaudeApiClient`가 담당하며 `Spring RestClient`(Spring Boot 3.2+)로 HTTP 요청을 보낸다. `CrawlerStrategy` 엔티티를 추가하여 AI가 생성한 파싱 전략 JSON을 버전별로 보관한다. 수동 크롤링 트리거 REST API(`POST /admin/api/games/{id}/crawl`)도 이 스프린트에서 함께 구현한다.

**Tech Stack:** Java 21, Spring Boot 3.2, Thymeleaf 3, Bootstrap 5 CDN, Spring RestClient, Lombok, JPA/SQLite, Gradle

**브랜치:** `sprint3` (master에서 분기, 현재 체크아웃 상태)

---

## 스프린트 목표 및 범위

### 포함 항목

- Task 1: Thymeleaf 공통 레이아웃 + 대시보드 UI (S3-1)
- Task 2: 게임 목록 UI + 수동 크롤링 트리거 API (S3-2 일부)
- Task 3: 게임 등록 UI (S3-2 일부)
- Task 4: 게임 상세 UI — 배경화면 탭 + LocalStorageService.listFiles() 구현 (S3-2 일부)
- Task 5: 게임 상세 UI — 파싱 전략 탭 + 크롤링 로그 탭 (S3-2 일부)
- Task 6: CrawlerStrategy 엔티티 + Claude API 클라이언트 (S3-3)
- Task 7: AI 분석 엔드포인트 + 게임 등록 폼 폴링 연결 (S3-3 완성)

### 제외 항목 (Sprint 4 이후)

- AI 분석 비동기 처리 (`@Async`) — Sprint 4
- GenericCrawlerExecutor — Sprint 4
- 자동 태그 생성 — Sprint 5
- 배경화면 개별 삭제 (UI에 버튼은 노출, 동작은 Sprint 4)

---

## 완료 기준 (Definition of Done)

| 기준 | 검증 방법 |
|------|-----------|
| `/admin` 대시보드에 요약 카드(게임 수, 배경화면 수, 마지막 크롤링 시각)가 표시된다 | 브라우저 접속 확인 |
| 크롤러 상태별 게임 수(ACTIVE/UPDATING/FAILED) 뱃지가 보인다 | 브라우저 확인 |
| 최근 크롤링 로그 10건이 타임라인으로 표시된다 | 브라우저 확인 |
| `/admin/games`에서 게임 테이블과 상태 뱃지가 표시된다 | 브라우저 확인 |
| 게임 행의 "크롤링 실행" 버튼을 누르면 즉시 크롤링이 시작된다 | `POST /admin/api/games/{id}/crawl` 200 응답 |
| `/admin/games/new`에서 게임명 + URL 폼 입력이 동작한다 | 브라우저 확인 |
| `/admin/games/{id}`에서 3개 탭(배경화면/전략/로그)이 전환된다 | 브라우저 클릭 확인 |
| 배경화면 탭에 썸네일 갤러리가 표시된다 | 브라우저 확인 |
| Claude API 호출이 정상 수행되고 JSON 응답이 파싱된다 | 단위 테스트 통과 |
| AI 분석 버튼 클릭 시 결과 JSON이 textarea에 표시된다 | 브라우저 확인 |

---

## 기존 코드 참고

### 엔티티 요약

| 엔티티 | 테이블 | 주요 필드 |
|--------|--------|-----------|
| `Game` | `games` | id, name, url, status(ACTIVE/UPDATING/FAILED), createdAt, lastCrawledAt |
| `Wallpaper` | `wallpapers` | id, gameId, fileName, url, width, height, blurHash, tags, description, imageType, createdAt |
| `CrawlingLog` | `crawling_logs` | id, gameId, startedAt, finishedAt, collectedCount, status(RUNNING/SUCCESS/FAILED), errorMessage |

### Repository 메서드 (기존)

```java
// GameRepository
List<Game> findAllByStatus(GameStatus status);

// WallpaperRepository
Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
long countByGameId(Long gameId);
boolean existsByGameIdAndFileName(Long gameId, String fileName);

// CrawlingLogRepository
List<CrawlingLog> findTop10ByOrderByStartedAtDesc();
List<CrawlingLog> findAllByGameIdOrderByStartedAtDesc(Long gameId);
```

### StorageService (기존, listFiles는 미구현)

```java
// Task 4에서 listFiles() 구현
List<String> listFiles(Long gameId); // 현재 UnsupportedOperationException
```

---

## Task 1: Thymeleaf 공통 레이아웃 + 대시보드 UI

**Files:**
- Create: `server/src/main/resources/templates/layout/base.html`
- Create: `server/src/main/resources/templates/admin/dashboard.html`
- Create: `server/src/main/java/com/gamepaper/admin/AdminDashboardController.java`
- Create: `server/src/main/java/com/gamepaper/admin/dto/DashboardData.java`

### Step 1: AdminDashboardController + DashboardData DTO 작성

```java
// server/src/main/java/com/gamepaper/admin/dto/DashboardData.java
package com.gamepaper.admin.dto;

import com.gamepaper.domain.crawler.CrawlingLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class DashboardData {
    private final long totalGames;
    private final long totalWallpapers;
    private final LocalDateTime lastCrawledAt;   // 전체 게임 중 가장 최근 크롤링 시각
    private final long activeCount;
    private final long updatingCount;
    private final long failedCount;
    private final List<CrawlingLog> recentLogs;  // 최근 10건
}
```

```java
// server/src/main/java/com/gamepaper/admin/AdminDashboardController.java
package com.gamepaper.admin;

import com.gamepaper.admin.dto.DashboardData;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.game.GameStatus;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;
    private final CrawlingLogRepository crawlingLogRepository;

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        long totalGames = gameRepository.count();
        long totalWallpapers = wallpaperRepository.count();
        long activeCount = gameRepository.findAllByStatus(GameStatus.ACTIVE).size();
        long updatingCount = gameRepository.findAllByStatus(GameStatus.UPDATING).size();
        long failedCount = gameRepository.findAllByStatus(GameStatus.FAILED).size();

        // 가장 최근 크롤링 완료 시각: 최근 로그 10건 중 finishedAt이 null이 아닌 첫 번째
        LocalDateTime lastCrawledAt = crawlingLogRepository
                .findTop10ByOrderByStartedAtDesc()
                .stream()
                .filter(log -> log.getFinishedAt() != null)
                .map(log -> log.getFinishedAt())
                .findFirst()
                .orElse(null);

        DashboardData data = new DashboardData(
                totalGames, totalWallpapers, lastCrawledAt,
                activeCount, updatingCount, failedCount,
                crawlingLogRepository.findTop10ByOrderByStartedAtDesc()
        );

        model.addAttribute("data", data);
        return "admin/dashboard";
    }
}
```

### Step 2: 공통 레이아웃 HTML (Thymeleaf Fragment) 작성

```html
<!-- server/src/main/resources/templates/layout/base.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:fragment="layout(title, content)">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:replace="${title}">GamePaper Admin</title>
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
  <style>
    body { background-color: #f8f9fa; }
    .sidebar {
      min-height: 100vh;
      background-color: #212529;
      width: 220px;
      flex-shrink: 0;
    }
    .sidebar .nav-link { color: #adb5bd; }
    .sidebar .nav-link:hover, .sidebar .nav-link.active { color: #fff; background-color: #495057; border-radius: 4px; }
    .sidebar .brand { color: #fff; font-size: 1.2rem; font-weight: bold; padding: 1rem; display: block; }
    .main-content { flex: 1; overflow-x: auto; }
    .status-badge-active    { background-color: #198754; }
    .status-badge-updating  { background-color: #0d6efd; }
    .status-badge-failed    { background-color: #dc3545; }
    .status-badge-running   { background-color: #ffc107; color: #000; }
  </style>
</head>
<body>
<div class="d-flex">
  <!-- 사이드바 -->
  <nav class="sidebar d-flex flex-column p-2">
    <a class="brand" th:href="@{/admin}">
      <i class="bi bi-collection-play me-2"></i>GamePaper
    </a>
    <ul class="nav flex-column mt-3">
      <li class="nav-item">
        <a class="nav-link" th:href="@{/admin}"
           th:classappend="${#httpServletRequest.requestURI == '/admin' || #httpServletRequest.requestURI == '/admin/'} ? 'active' : ''">
          <i class="bi bi-speedometer2 me-2"></i>대시보드
        </a>
      </li>
      <li class="nav-item">
        <a class="nav-link" th:href="@{/admin/games}"
           th:classappend="${#httpServletRequest.requestURI.startsWith('/admin/games')} ? 'active' : ''">
          <i class="bi bi-controller me-2"></i>게임 목록
        </a>
      </li>
    </ul>
  </nav>

  <!-- 메인 영역 -->
  <div class="main-content">
    <!-- 상단 헤더 -->
    <header class="bg-white border-bottom px-4 py-3 d-flex align-items-center">
      <h5 class="mb-0 text-secondary" th:replace="${title}">페이지 제목</h5>
    </header>
    <!-- 페이지 콘텐츠 -->
    <main class="p-4" th:replace="${content}">
      콘텐츠
    </main>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

### Step 3: 대시보드 페이지 HTML 작성

```html
<!-- server/src/main/resources/templates/admin/dashboard.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head>
  <title>대시보드 - GamePaper Admin</title>
</head>
<body>
<main>
  <!-- 요약 카드 -->
  <div class="row g-3 mb-4">
    <div class="col-md-3">
      <div class="card text-center shadow-sm">
        <div class="card-body">
          <h2 class="card-title text-primary" th:text="${data.totalGames}">0</h2>
          <p class="card-text text-muted">전체 게임</p>
        </div>
      </div>
    </div>
    <div class="col-md-3">
      <div class="card text-center shadow-sm">
        <div class="card-body">
          <h2 class="card-title text-success" th:text="${data.totalWallpapers}">0</h2>
          <p class="card-text text-muted">총 배경화면</p>
        </div>
      </div>
    </div>
    <div class="col-md-3">
      <div class="card text-center shadow-sm">
        <div class="card-body">
          <h2 class="card-title text-info"
              th:text="${data.lastCrawledAt != null ? #temporals.format(data.lastCrawledAt, 'MM-dd HH:mm') : '-'}">-</h2>
          <p class="card-text text-muted">마지막 크롤링</p>
        </div>
      </div>
    </div>
    <div class="col-md-3">
      <div class="card text-center shadow-sm">
        <div class="card-body py-3">
          <div class="d-flex justify-content-center gap-2 flex-wrap">
            <span class="badge status-badge-active fs-6" th:text="${data.activeCount} + ' ACTIVE'">0 ACTIVE</span>
            <span class="badge status-badge-updating fs-6" th:text="${data.updatingCount} + ' UPDATING'">0 UPDATING</span>
            <span class="badge status-badge-failed fs-6" th:text="${data.failedCount} + ' FAILED'">0 FAILED</span>
          </div>
          <p class="card-text text-muted mt-2">크롤러 상태</p>
        </div>
      </div>
    </div>
  </div>

  <!-- 최근 크롤링 로그 -->
  <div class="card shadow-sm">
    <div class="card-header bg-white">
      <h6 class="mb-0"><i class="bi bi-clock-history me-2"></i>최근 크롤링 로그 (최근 10건)</h6>
    </div>
    <div class="card-body p-0">
      <div th:if="${#lists.isEmpty(data.recentLogs)}" class="text-center text-muted py-4">
        크롤링 이력이 없습니다.
      </div>
      <ul class="list-group list-group-flush" th:unless="${#lists.isEmpty(data.recentLogs)}">
        <li class="list-group-item d-flex justify-content-between align-items-center"
            th:each="log : ${data.recentLogs}">
          <div>
            <span class="badge me-2"
                  th:classappend="${log.status.name() == 'SUCCESS'} ? 'bg-success' : (${log.status.name() == 'FAILED'} ? 'bg-danger' : 'bg-warning text-dark')"
                  th:text="${log.status}">STATUS</span>
            <span class="text-muted small">Game ID: </span>
            <a th:href="@{/admin/games/{id}(id=${log.gameId})}"
               class="text-decoration-none" th:text="${log.gameId}">1</a>
            <span th:if="${log.status.name() == 'SUCCESS'}" class="ms-2 text-muted small"
                  th:text="'수집: ' + ${log.collectedCount} + '개'"></span>
            <span th:if="${log.errorMessage != null}" class="ms-2 text-danger small"
                  th:text="${#strings.abbreviate(log.errorMessage, 60)}"></span>
          </div>
          <span class="text-muted small"
                th:text="${log.startedAt != null ? #temporals.format(log.startedAt, 'MM-dd HH:mm:ss') : '-'}">-</span>
        </li>
      </ul>
    </div>
  </div>
</main>
</body>
</html>
```

### Step 4: 서버 실행 후 브라우저 확인

```bash
cd D:/work/AI해커톤/server
./gradlew bootRun
# 브라우저에서 http://localhost:8080/admin 접속
# 요약 카드 4개와 크롤링 로그 섹션이 표시되는지 확인
```

예상 화면: 게임 수 카드, 배경화면 수 카드, 마지막 크롤링 카드, 상태 뱃지 카드, 로그 목록

### Step 5: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/ \
        server/src/main/resources/templates/
git commit -m "feat: 관리자 대시보드 레이아웃 및 UI 구현 (Task 1)"
```

---

## Task 2: 게임 목록 UI + 수동 크롤링 트리거 API

**Files:**
- Create: `server/src/main/java/com/gamepaper/admin/AdminGameController.java`
- Create: `server/src/main/java/com/gamepaper/admin/dto/GameListItem.java`
- Create: `server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java`
- Create: `server/src/main/resources/templates/admin/game-list.html`

### Step 1: GameListItem DTO 작성

```java
// server/src/main/java/com/gamepaper/admin/dto/GameListItem.java
package com.gamepaper.admin.dto;

import com.gamepaper.domain.game.Game;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GameListItem {
    private final Long id;
    private final String name;
    private final String url;
    private final String status;       // ACTIVE / UPDATING / FAILED
    private final long wallpaperCount;
    private final LocalDateTime lastCrawledAt;

    public GameListItem(Game game, long wallpaperCount) {
        this.id = game.getId();
        this.name = game.getName();
        this.url = game.getUrl();
        this.status = game.getStatus().name();
        this.wallpaperCount = wallpaperCount;
        this.lastCrawledAt = game.getLastCrawledAt();
    }
}
```

### Step 2: AdminGameController 작성

```java
// server/src/main/java/com/gamepaper/admin/AdminGameController.java
package com.gamepaper.admin;

import com.gamepaper.admin.dto.GameListItem;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/games")
@RequiredArgsConstructor
public class AdminGameController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;

    // 게임 목록
    @GetMapping
    public String list(Model model) {
        List<GameListItem> items = gameRepository.findAll().stream()
                .map(game -> new GameListItem(game, wallpaperRepository.countByGameId(game.getId())))
                .collect(Collectors.toList());
        model.addAttribute("games", items);
        return "admin/game-list";
    }

    // 게임 등록 폼 (Task 3에서 구현)
    @GetMapping("/new")
    public String newGameForm(Model model) {
        model.addAttribute("game", new Game());
        return "admin/game-new";
    }

    // 게임 상태 토글 (활성화/비활성화)
    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        gameRepository.findById(id).ifPresent(game -> {
            if (game.getStatus() == com.gamepaper.domain.game.GameStatus.ACTIVE) {
                game.setStatus(com.gamepaper.domain.game.GameStatus.FAILED); // 비활성화는 FAILED로 표시
            } else {
                game.setStatus(com.gamepaper.domain.game.GameStatus.ACTIVE);
            }
            gameRepository.save(game);
        });
        ra.addFlashAttribute("message", "게임 상태가 변경되었습니다.");
        return "redirect:/admin/games";
    }

    // 게임 삭제
    @PostMapping("/{id}/delete")
    public String deleteGame(@PathVariable Long id, RedirectAttributes ra) {
        gameRepository.deleteById(id);
        ra.addFlashAttribute("message", "게임이 삭제되었습니다.");
        return "redirect:/admin/games";
    }
}
```

### Step 3: 수동 크롤링 트리거 REST API 작성

```java
// server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java
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

        // 비동기로 크롤러 실행 (블로킹 방지)
        new Thread(() -> crawlerScheduler.runSingle(target)).start();
        log.info("수동 크롤링 트리거 - gameId={}", id);
        return ResponseEntity.ok(Map.of("message", "크롤링을 시작했습니다."));
    }
}
```

**주의:** `CrawlerScheduler.runSingle()`은 현재 `private`이므로 `public`으로 변경해야 합니다.

```java
// server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java
// runSingle() 메서드 접근 제한자를 private -> public으로 변경
public void runSingle(GameCrawler crawler) {
    // ... 기존 코드 동일 ...
}
```

### Step 4: 게임 목록 HTML 작성

```html
<!-- server/src/main/resources/templates/admin/game-list.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head>
  <title>게임 목록 - GamePaper Admin</title>
</head>
<body>
<main>
  <!-- 플래시 메시지 -->
  <div th:if="${message}" class="alert alert-success alert-dismissible fade show" role="alert">
    <span th:text="${message}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  </div>

  <!-- 액션 바 -->
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h6 class="mb-0 text-muted" th:text="'총 ' + ${#lists.size(games)} + '개 게임'">총 0개 게임</h6>
    <a th:href="@{/admin/games/new}" class="btn btn-primary btn-sm">
      <i class="bi bi-plus-lg me-1"></i>게임 등록
    </a>
  </div>

  <!-- 게임 테이블 -->
  <div class="card shadow-sm">
    <div class="card-body p-0">
      <div th:if="${#lists.isEmpty(games)}" class="text-center text-muted py-5">
        <i class="bi bi-controller fs-1 d-block mb-2"></i>
        등록된 게임이 없습니다. 게임을 등록해주세요.
      </div>
      <table class="table table-hover mb-0" th:unless="${#lists.isEmpty(games)}">
        <thead class="table-light">
          <tr>
            <th>게임명</th>
            <th>URL</th>
            <th class="text-center">배경화면</th>
            <th class="text-center">상태</th>
            <th>마지막 크롤링</th>
            <th class="text-center">액션</th>
          </tr>
        </thead>
        <tbody>
          <tr th:each="game : ${games}">
            <td>
              <a th:href="@{/admin/games/{id}(id=${game.id})}"
                 class="text-decoration-none fw-semibold" th:text="${game.name}">게임명</a>
            </td>
            <td>
              <a th:href="${game.url}" target="_blank" class="text-muted small"
                 th:text="${#strings.abbreviate(game.url, 50)}">URL</a>
            </td>
            <td class="text-center" th:text="${game.wallpaperCount}">0</td>
            <td class="text-center">
              <span class="badge"
                    th:classappend="${game.status == 'ACTIVE'} ? 'bg-success' : (${game.status == 'UPDATING'} ? 'bg-primary' : 'bg-danger')"
                    th:text="${game.status}">ACTIVE</span>
            </td>
            <td class="text-muted small"
                th:text="${game.lastCrawledAt != null ? #temporals.format(game.lastCrawledAt, 'yyyy-MM-dd HH:mm') : '-'}">-</td>
            <td class="text-center">
              <div class="btn-group btn-group-sm">
                <!-- 크롤링 실행 버튼 -->
                <button class="btn btn-outline-primary btn-crawl"
                        th:data-game-id="${game.id}"
                        title="즉시 크롤링 실행">
                  <i class="bi bi-arrow-clockwise"></i>
                </button>
                <!-- 상태 토글 -->
                <form th:action="@{/admin/games/{id}/toggle-status(id=${game.id})}" method="post" style="display:inline">
                  <button type="submit" class="btn btn-outline-secondary" title="상태 전환">
                    <i class="bi bi-toggle-on"></i>
                  </button>
                </form>
                <!-- 삭제 -->
                <form th:action="@{/admin/games/{id}/delete(id=${game.id})}" method="post" style="display:inline"
                      onsubmit="return confirm('게임을 삭제하시겠습니까? 배경화면 데이터는 DB에서 제거됩니다.')">
                  <button type="submit" class="btn btn-outline-danger" title="삭제">
                    <i class="bi bi-trash"></i>
                  </button>
                </form>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <!-- 크롤링 상태 토스트 -->
  <div class="toast-container position-fixed bottom-0 end-0 p-3">
    <div id="crawlToast" class="toast" role="alert">
      <div class="toast-header">
        <strong class="me-auto">크롤링</strong>
        <button type="button" class="btn-close" data-bs-dismiss="toast"></button>
      </div>
      <div class="toast-body" id="crawlToastMsg">크롤링을 시작했습니다.</div>
    </div>
  </div>

  <script>
    // 크롤링 실행 버튼 클릭 핸들러
    document.querySelectorAll('.btn-crawl').forEach(btn => {
      btn.addEventListener('click', function() {
        const gameId = this.dataset.gameId;
        fetch(`/admin/api/games/${gameId}/crawl`, { method: 'POST' })
          .then(r => r.json())
          .then(data => {
            document.getElementById('crawlToastMsg').textContent = data.message;
            new bootstrap.Toast(document.getElementById('crawlToast')).show();
          })
          .catch(() => {
            document.getElementById('crawlToastMsg').textContent = '크롤링 요청 실패';
            new bootstrap.Toast(document.getElementById('crawlToast')).show();
          });
      });
    });
  </script>
</main>
</body>
</html>
```

### Step 5: 브라우저 확인

```bash
# 서버 재기동 후
# http://localhost:8080/admin/games 접속
# 게임 테이블과 액션 버튼 확인
# 크롤링 버튼 클릭 후 토스트 메시지 확인
```

### Step 6: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/ \
        server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java \
        server/src/main/resources/templates/admin/game-list.html
git commit -m "feat: 게임 목록 UI 및 수동 크롤링 트리거 API 구현 (Task 2)"
```

---

## Task 3: 게임 등록 UI

**Files:**
- Create: `server/src/main/resources/templates/admin/game-new.html`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminGameController.java` — 등록 POST 핸들러 추가

### Step 1: AdminGameController에 POST 핸들러 추가

```java
// AdminGameController.java에 추가

// 게임 등록 처리 (AI 분석 없이 직접 저장)
@PostMapping("/new")
public String createGame(@RequestParam String name,
                         @RequestParam String url,
                         RedirectAttributes ra) {
    if (name.isBlank() || url.isBlank()) {
        ra.addFlashAttribute("error", "게임명과 URL을 모두 입력해주세요.");
        return "redirect:/admin/games/new";
    }
    Game game = new Game(name, url);
    gameRepository.save(game);
    ra.addFlashAttribute("message", "게임 '" + name + "'이(가) 등록되었습니다.");
    return "redirect:/admin/games/" + game.getId();
}
```

### Step 2: 게임 등록 폼 HTML 작성

```html
<!-- server/src/main/resources/templates/admin/game-new.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head>
  <title>게임 등록 - GamePaper Admin</title>
</head>
<body>
<main>
  <div class="row justify-content-center">
    <div class="col-lg-8">
      <!-- 에러 메시지 -->
      <div th:if="${error}" class="alert alert-danger alert-dismissible fade show">
        <span th:text="${error}"></span>
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
      </div>

      <div class="card shadow-sm">
        <div class="card-header bg-white">
          <h6 class="mb-0"><i class="bi bi-plus-circle me-2"></i>게임 등록</h6>
        </div>
        <div class="card-body">
          <!-- 등록 폼 -->
          <form id="gameForm" th:action="@{/admin/games/new}" method="post">
            <div class="mb-3">
              <label for="name" class="form-label fw-semibold">게임명 <span class="text-danger">*</span></label>
              <input type="text" class="form-control" id="name" name="name"
                     placeholder="예: 원신, 메이플스토리" required>
            </div>
            <div class="mb-3">
              <label for="url" class="form-label fw-semibold">배경화면 페이지 URL <span class="text-danger">*</span></label>
              <input type="url" class="form-control" id="url" name="url"
                     placeholder="https://example.com/wallpapers" required>
              <div class="form-text">게임 공식 사이트의 배경화면 다운로드 페이지 URL을 입력하세요.</div>
            </div>

            <!-- AI 분석 섹션 -->
            <hr>
            <div class="mb-3">
              <label class="form-label fw-semibold">AI 파싱 전략 분석 <span class="badge bg-secondary">선택</span></label>
              <div class="input-group">
                <button type="button" id="btnAnalyze" class="btn btn-outline-secondary">
                  <i class="bi bi-robot me-1"></i>AI 분석 시작
                </button>
                <span class="input-group-text text-muted small" id="analyzeStatus">
                  URL 입력 후 AI 분석을 실행하면 파싱 전략이 자동 생성됩니다.
                </span>
              </div>
            </div>

            <!-- 분석 결과 진행 표시 (숨김) -->
            <div id="analyzeProgress" class="mb-3 d-none">
              <div class="d-flex align-items-center gap-2 mb-2">
                <div class="spinner-border spinner-border-sm text-primary" id="analyzeSpinner"></div>
                <span id="analyzeProgressMsg" class="text-muted small">AI 분석 중...</span>
              </div>
            </div>

            <!-- 파싱 전략 미리보기 (숨김 → AI 분석 완료 후 표시) -->
            <div id="strategyPreview" class="mb-3 d-none">
              <label class="form-label fw-semibold">파싱 전략 미리보기 (읽기 전용)</label>
              <textarea id="strategyJson" class="form-control font-monospace" rows="10" readonly
                        style="font-size: 0.85rem;"></textarea>
              <div class="form-text text-success">
                <i class="bi bi-check-circle me-1"></i>AI 분석이 완료되었습니다. 저장 후 크롤링을 시작합니다.
              </div>
            </div>

            <!-- 액션 버튼 -->
            <div class="d-flex gap-2 mt-4">
              <button type="submit" class="btn btn-primary">
                <i class="bi bi-save me-1"></i>저장 (크롤링은 게임 상세 페이지에서 실행)
              </button>
              <a th:href="@{/admin/games}" class="btn btn-outline-secondary">취소</a>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>

  <script>
    const btnAnalyze = document.getElementById('btnAnalyze');
    const analyzeStatus = document.getElementById('analyzeStatus');
    const analyzeProgress = document.getElementById('analyzeProgress');
    const analyzeProgressMsg = document.getElementById('analyzeProgressMsg');
    const strategyPreview = document.getElementById('strategyPreview');
    const strategyJson = document.getElementById('strategyJson');

    btnAnalyze.addEventListener('click', function() {
      const url = document.getElementById('url').value.trim();
      if (!url) {
        alert('URL을 먼저 입력해주세요.');
        return;
      }

      btnAnalyze.disabled = true;
      analyzeProgress.classList.remove('d-none');
      strategyPreview.classList.add('d-none');
      analyzeProgressMsg.textContent = 'AI가 페이지를 분석하는 중입니다... (최대 60초 소요)';

      fetch('/admin/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url })
      })
      .then(r => {
        if (!r.ok) throw new Error('분석 실패: ' + r.status);
        return r.json();
      })
      .then(data => {
        analyzeProgress.classList.add('d-none');
        strategyPreview.classList.remove('d-none');
        strategyJson.value = JSON.stringify(data.strategy, null, 2);
        analyzeStatus.textContent = '분석 완료';
        btnAnalyze.disabled = false;
      })
      .catch(err => {
        analyzeProgress.classList.add('d-none');
        analyzeStatus.textContent = '분석 실패: ' + err.message;
        btnAnalyze.disabled = false;
      });
    });
  </script>
</main>
</body>
</html>
```

### Step 3: 브라우저 확인

```bash
# http://localhost:8080/admin/games/new 접속
# 게임명 + URL 입력 후 "저장" 버튼 클릭
# /admin/games/{id} 로 리다이렉트되는지 확인 (상세 페이지는 Task 4에서 구현)
```

### Step 4: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/AdminGameController.java \
        server/src/main/resources/templates/admin/game-new.html
git commit -m "feat: 게임 등록 UI 폼 구현 (Task 3)"
```

---

## Task 4: 게임 상세 UI — 배경화면 탭 + listFiles() 구현

**Files:**
- Create: `server/src/main/resources/templates/admin/game-detail.html`
- Modify: `server/src/main/java/com/gamepaper/admin/AdminGameController.java` — 상세 페이지 핸들러 추가
- Modify: `server/src/main/java/com/gamepaper/storage/local/LocalStorageService.java` — listFiles() 구현
- Create: `server/src/test/java/com/gamepaper/storage/LocalStorageServiceTest.java` — listFiles 테스트

### Step 1: LocalStorageService.listFiles() 테스트 작성

```java
// server/src/test/java/com/gamepaper/storage/LocalStorageServiceTest.java
package com.gamepaper.storage;

import com.gamepaper.storage.local.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    LocalStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(tempDir.toString(), "http://localhost:8080");
    }

    @Test
    void listFiles_빈폴더_빈목록_반환() {
        List<String> files = service.listFiles(1L);
        assertThat(files).isEmpty();
    }

    @Test
    void listFiles_파일_업로드_후_목록_반환() {
        service.upload(1L, "wallpaper1.jpg", new byte[]{1, 2, 3});
        service.upload(1L, "wallpaper2.jpg", new byte[]{4, 5, 6});

        List<String> files = service.listFiles(1L);
        assertThat(files).hasSize(2);
        assertThat(files).containsExactlyInAnyOrder("wallpaper1.jpg", "wallpaper2.jpg");
    }

    @Test
    void listFiles_존재하지않는_gameId_빈목록_반환() {
        List<String> files = service.listFiles(999L);
        assertThat(files).isEmpty();
    }
}
```

### Step 2: 테스트 실행하여 실패 확인

```bash
cd D:/work/AI해커톤/server
./gradlew test --tests "com.gamepaper.storage.LocalStorageServiceTest" --info
# listFiles()가 UnsupportedOperationException을 던지므로 실패 예상
```

### Step 3: LocalStorageService.listFiles() 구현

```java
// LocalStorageService.java — listFiles() 메서드 교체
@Override
public List<String> listFiles(Long gameId) {
    Path dir = Paths.get(storageRoot, "images", String.valueOf(gameId));
    if (!Files.exists(dir)) {
        return List.of();
    }
    try (var stream = Files.list(dir)) {
        return stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    } catch (IOException e) {
        log.warn("파일 목록 조회 실패: gameId={}", gameId, e);
        return List.of();
    }
}
```

### Step 4: 테스트 재실행 — PASS 확인

```bash
./gradlew test --tests "com.gamepaper.storage.LocalStorageServiceTest"
# 3개 테스트 모두 PASS
```

### Step 5: AdminGameController에 상세 페이지 핸들러 추가

```java
// AdminGameController.java에 추가

@GetMapping("/{id}")
public String detail(@PathVariable Long id, Model model) {
    Game game = gameRepository.findById(id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다."));

    // 배경화면 목록 (최신순, 최대 100개)
    var pageable = org.springframework.data.domain.PageRequest.of(0, 100,
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    var wallpapers = wallpaperRepository.findAllByGameId(id, pageable).getContent();

    // 크롤링 로그
    var logs = crawlingLogRepository.findAllByGameIdOrderByStartedAtDesc(id);

    model.addAttribute("game", game);
    model.addAttribute("wallpapers", wallpapers);
    model.addAttribute("logs", logs);
    return "admin/game-detail";
}
```

`crawlingLogRepository` 필드를 `AdminGameController`에 추가해야 합니다:

```java
// AdminGameController 필드 추가
private final com.gamepaper.domain.crawler.CrawlingLogRepository crawlingLogRepository;
```

### Step 6: 게임 상세 HTML 작성 (배경화면 탭 포함)

```html
<!-- server/src/main/resources/templates/admin/game-detail.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head>
  <title th:text="${game.name} + ' - GamePaper Admin'">게임 상세</title>
</head>
<body>
<main>
  <!-- 게임 헤더 -->
  <div class="d-flex justify-content-between align-items-start mb-4">
    <div>
      <h5 class="mb-1" th:text="${game.name}">게임명</h5>
      <a th:href="${game.url}" target="_blank" class="text-muted small" th:text="${game.url}">URL</a>
    </div>
    <div class="d-flex gap-2">
      <span class="badge fs-6"
            th:classappend="${game.status.name() == 'ACTIVE'} ? 'bg-success' : (${game.status.name() == 'UPDATING'} ? 'bg-primary' : 'bg-danger')"
            th:text="${game.status}">ACTIVE</span>
      <button class="btn btn-sm btn-outline-primary btn-crawl"
              th:data-game-id="${game.id}">
        <i class="bi bi-arrow-clockwise me-1"></i>크롤링 실행
      </button>
      <a th:href="@{/admin/games}" class="btn btn-sm btn-outline-secondary">
        <i class="bi bi-arrow-left me-1"></i>목록
      </a>
    </div>
  </div>

  <!-- 탭 네비게이션 -->
  <ul class="nav nav-tabs mb-3" id="detailTabs">
    <li class="nav-item">
      <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#wallpapersTab">
        <i class="bi bi-images me-1"></i>배경화면
        <span class="badge bg-secondary ms-1" th:text="${#lists.size(wallpapers)}">0</span>
      </button>
    </li>
    <li class="nav-item">
      <button class="nav-link" data-bs-toggle="tab" data-bs-target="#strategyTab">
        <i class="bi bi-code-square me-1"></i>파싱 전략
      </button>
    </li>
    <li class="nav-item">
      <button class="nav-link" data-bs-toggle="tab" data-bs-target="#logsTab">
        <i class="bi bi-list-ul me-1"></i>크롤링 로그
        <span class="badge bg-secondary ms-1" th:text="${#lists.size(logs)}">0</span>
      </button>
    </li>
  </ul>

  <div class="tab-content">
    <!-- 배경화면 탭 -->
    <div class="tab-pane fade show active" id="wallpapersTab">
      <div th:if="${#lists.isEmpty(wallpapers)}" class="text-center text-muted py-5">
        <i class="bi bi-image fs-1 d-block mb-2"></i>
        수집된 배경화면이 없습니다. 크롤링을 실행해주세요.
      </div>
      <div class="row g-2" th:unless="${#lists.isEmpty(wallpapers)}">
        <div class="col-6 col-md-3 col-lg-2" th:each="wp : ${wallpapers}">
          <div class="card h-100 shadow-sm">
            <img th:src="${wp.url}" class="card-img-top"
                 style="height: 120px; object-fit: cover;"
                 th:alt="${wp.fileName}"
                 loading="lazy">
            <div class="card-body p-1">
              <p class="card-text text-muted" style="font-size: 0.7rem;"
                 th:text="${wp.width != null ? wp.width + 'x' + wp.height : '-'}">해상도</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 파싱 전략 탭 (Task 5에서 내용 채움) -->
    <div class="tab-pane fade" id="strategyTab">
      <div id="strategyTabContent">
        <!-- Task 5에서 CrawlerStrategy 구현 후 교체 -->
        <div class="text-center text-muted py-5">
          <i class="bi bi-robot fs-1 d-block mb-2"></i>
          <p>파싱 전략이 없습니다.</p>
          <button class="btn btn-outline-primary btn-sm" id="btnAnalyzeDetail"
                  th:data-game-id="${game.id}">
            <i class="bi bi-robot me-1"></i>AI 분석 시작
          </button>
          <div id="analyzeDetailProgress" class="mt-3 d-none">
            <div class="spinner-border spinner-border-sm text-primary me-2"></div>
            <span class="text-muted small">AI 분석 중... (최대 60초)</span>
          </div>
          <div id="strategyResult" class="mt-3 d-none text-start">
            <label class="form-label fw-semibold">생성된 파싱 전략</label>
            <textarea id="strategyJsonArea" class="form-control font-monospace" rows="12" readonly
                      style="font-size: 0.85rem;"></textarea>
          </div>
        </div>
      </div>
    </div>

    <!-- 크롤링 로그 탭 -->
    <div class="tab-pane fade" id="logsTab">
      <div th:if="${#lists.isEmpty(logs)}" class="text-center text-muted py-5">
        크롤링 이력이 없습니다.
      </div>
      <div class="table-responsive" th:unless="${#lists.isEmpty(logs)}">
        <table class="table table-sm table-hover">
          <thead class="table-light">
            <tr>
              <th>시작 시각</th>
              <th>완료 시각</th>
              <th class="text-center">상태</th>
              <th class="text-center">수집 수</th>
              <th>에러 메시지</th>
            </tr>
          </thead>
          <tbody>
            <tr th:each="log : ${logs}">
              <td class="small" th:text="${log.startedAt != null ? #temporals.format(log.startedAt, 'yyyy-MM-dd HH:mm:ss') : '-'}">-</td>
              <td class="small" th:text="${log.finishedAt != null ? #temporals.format(log.finishedAt, 'yyyy-MM-dd HH:mm:ss') : '진행 중'}">-</td>
              <td class="text-center">
                <span class="badge"
                      th:classappend="${log.status.name() == 'SUCCESS'} ? 'bg-success' : (${log.status.name() == 'FAILED'} ? 'bg-danger' : 'bg-warning text-dark')"
                      th:text="${log.status}">STATUS</span>
              </td>
              <td class="text-center" th:text="${log.collectedCount}">0</td>
              <td class="text-danger small" th:text="${log.errorMessage ?: '-'}">-</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- 크롤링 토스트 -->
  <div class="toast-container position-fixed bottom-0 end-0 p-3">
    <div id="crawlToast" class="toast">
      <div class="toast-header">
        <strong class="me-auto">크롤링</strong>
        <button type="button" class="btn-close" data-bs-dismiss="toast"></button>
      </div>
      <div class="toast-body" id="crawlToastMsg">크롤링을 시작했습니다.</div>
    </div>
  </div>

  <script th:inline="javascript">
    const gameId = /*[[${game.id}]]*/ 0;

    // 크롤링 버튼
    document.querySelector('.btn-crawl')?.addEventListener('click', function() {
      fetch(`/admin/api/games/${gameId}/crawl`, { method: 'POST' })
        .then(r => r.json())
        .then(data => {
          document.getElementById('crawlToastMsg').textContent = data.message;
          new bootstrap.Toast(document.getElementById('crawlToast')).show();
        });
    });

    // AI 분석 버튼 (상세 페이지 전략 탭)
    document.getElementById('btnAnalyzeDetail')?.addEventListener('click', function() {
      const gameUrl = /*[[${game.url}]]*/ '';
      this.disabled = true;
      document.getElementById('analyzeDetailProgress').classList.remove('d-none');

      fetch('/admin/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: gameUrl, gameId: gameId })
      })
      .then(r => {
        if (!r.ok) throw new Error(r.status);
        return r.json();
      })
      .then(data => {
        document.getElementById('analyzeDetailProgress').classList.add('d-none');
        document.getElementById('strategyResult').classList.remove('d-none');
        document.getElementById('strategyJsonArea').value = JSON.stringify(data.strategy, null, 2);
        this.disabled = false;
      })
      .catch(err => {
        document.getElementById('analyzeDetailProgress').classList.add('d-none');
        alert('AI 분석 실패: ' + err.message);
        this.disabled = false;
      });
    });
  </script>
</main>
</body>
</html>
```

### Step 7: 브라우저 확인

```bash
# http://localhost:8080/admin/games/{id} 접속 (id는 실제 등록된 게임 ID)
# 3개 탭이 전환되는지 확인
# 배경화면 탭: 수집된 이미지 썸네일 표시 확인
# 크롤링 로그 탭: 이력 테이블 확인
```

### Step 8: 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/AdminGameController.java \
        server/src/main/java/com/gamepaper/storage/local/LocalStorageService.java \
        server/src/test/java/com/gamepaper/storage/LocalStorageServiceTest.java \
        server/src/main/resources/templates/admin/game-detail.html
git commit -m "feat: 게임 상세 UI 및 listFiles() 구현 (Task 4)"
```

---

## Task 5: 파싱 전략 탭 — CrawlerStrategy 엔티티 추가

**Files:**
- Create: `server/src/main/java/com/gamepaper/domain/strategy/CrawlerStrategy.java`
- Create: `server/src/main/java/com/gamepaper/domain/strategy/CrawlerStrategyRepository.java`
- Modify: `server/src/main/resources/templates/admin/game-detail.html` — 전략 탭 교체

### Step 1: CrawlerStrategy 엔티티 + Repository 작성

```java
// server/src/main/java/com/gamepaper/domain/strategy/CrawlerStrategy.java
package com.gamepaper.domain.strategy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_strategies")
@Getter
@Setter
@NoArgsConstructor
public class CrawlerStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "strategy_json", columnDefinition = "TEXT", nullable = false)
    private String strategyJson;

    // 버전 번호: 재분석 시 1씩 증가
    @Column(nullable = false)
    private Integer version = 1;

    // AI 분석 완료 시각
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public CrawlerStrategy(Long gameId, String strategyJson) {
        this.gameId = gameId;
        this.strategyJson = strategyJson;
        this.version = 1;
        this.analyzedAt = LocalDateTime.now();
    }
}
```

```java
// server/src/main/java/com/gamepaper/domain/strategy/CrawlerStrategyRepository.java
package com.gamepaper.domain.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CrawlerStrategyRepository extends JpaRepository<CrawlerStrategy, Long> {
    // 최신 버전 전략 조회
    Optional<CrawlerStrategy> findTopByGameIdOrderByVersionDesc(Long gameId);
    // 전략 이력 전체 (버전 내림차순)
    List<CrawlerStrategy> findAllByGameIdOrderByVersionDesc(Long gameId);
}
```

### Step 2: AdminGameController 상세 핸들러에 전략 데이터 추가

```java
// AdminGameController.detail() 메서드 수정
// 파라미터에 CrawlerStrategyRepository 추가 및 model에 전략 추가

// 필드 추가
private final com.gamepaper.domain.strategy.CrawlerStrategyRepository strategyRepository;

// detail() 핸들러에 추가
var strategies = strategyRepository.findAllByGameIdOrderByVersionDesc(id);
var latestStrategy = strategies.isEmpty() ? null : strategies.get(0);
model.addAttribute("strategies", strategies);
model.addAttribute("latestStrategy", latestStrategy);
```

### Step 3: 게임 상세 HTML 전략 탭 교체

game-detail.html의 `strategyTab` div를 다음으로 교체:

```html
<!-- 파싱 전략 탭 (strategyTab div 내부 교체) -->
<div class="tab-pane fade" id="strategyTab">
  <!-- 전략이 없는 경우 -->
  <div th:if="${latestStrategy == null}">
    <div class="text-center text-muted py-4">
      <i class="bi bi-robot fs-1 d-block mb-2"></i>
      <p>파싱 전략이 없습니다. AI 분석을 실행해주세요.</p>
      <button class="btn btn-outline-primary btn-sm" id="btnAnalyzeDetail"
              th:data-game-id="${game.id}">
        <i class="bi bi-robot me-1"></i>AI 분석 시작
      </button>
    </div>
    <div id="analyzeDetailProgress" class="mt-3 d-none text-center">
      <div class="spinner-border spinner-border-sm text-primary me-2"></div>
      <span class="text-muted small">AI 분석 중... (최대 60초)</span>
    </div>
    <div id="strategyResult" class="mt-3 d-none">
      <label class="form-label fw-semibold">생성된 파싱 전략</label>
      <textarea id="strategyJsonArea" class="form-control font-monospace" rows="12" readonly
                style="font-size: 0.85rem;"></textarea>
    </div>
  </div>

  <!-- 전략이 있는 경우 -->
  <div th:unless="${latestStrategy == null}">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <span class="badge bg-primary me-1">v<span th:text="${latestStrategy.version}">1</span></span>
        <span class="text-muted small"
              th:text="'분석일: ' + #temporals.format(latestStrategy.analyzedAt, 'yyyy-MM-dd HH:mm')">-</span>
      </div>
      <button class="btn btn-sm btn-outline-secondary" id="btnReanalyze"
              th:data-game-id="${game.id}">
        <i class="bi bi-robot me-1"></i>재분석
      </button>
    </div>
    <textarea class="form-control font-monospace" rows="15"
              style="font-size: 0.85rem;" th:text="${latestStrategy.strategyJson}"></textarea>

    <!-- 분석 이력 -->
    <div th:if="${#lists.size(strategies) > 1}" class="mt-4">
      <h6 class="text-muted">분석 이력</h6>
      <table class="table table-sm table-hover">
        <thead class="table-light">
          <tr><th>버전</th><th>분석 일시</th></tr>
        </thead>
        <tbody>
          <tr th:each="s : ${strategies}">
            <td th:text="'v' + ${s.version}">v1</td>
            <td class="small" th:text="${#temporals.format(s.analyzedAt, 'yyyy-MM-dd HH:mm')}">-</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 재분석 진행 표시 -->
    <div id="analyzeDetailProgress" class="mt-3 d-none text-center">
      <div class="spinner-border spinner-border-sm text-primary me-2"></div>
      <span class="text-muted small">재분석 중... (최대 60초)</span>
    </div>
  </div>
</div>
```

### Step 4: 커밋

```bash
git add server/src/main/java/com/gamepaper/domain/strategy/ \
        server/src/main/java/com/gamepaper/admin/AdminGameController.java \
        server/src/main/resources/templates/admin/game-detail.html
git commit -m "feat: CrawlerStrategy 엔티티 추가 및 파싱 전략 탭 구현 (Task 5)"
```

---

## Task 6: Claude API 클라이언트 구현

**Files:**
- Create: `server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java`
- Create: `server/src/main/java/com/gamepaper/claude/dto/AnalyzeRequest.java`
- Create: `server/src/main/java/com/gamepaper/claude/dto/AnalyzeResponse.java`
- Create: `server/src/main/java/com/gamepaper/claude/CrawlerStrategyParser.java`
- Create: `server/src/test/java/com/gamepaper/claude/CrawlerStrategyParserTest.java`

### Step 1: Claude API 환경변수 설정 확인

`application-local.yml`에 다음 추가:

```yaml
# application-local.yml 하단에 추가
claude:
  api-key: ${ANTHROPIC_API_KEY:}
  api-url: https://api.anthropic.com/v1/messages
  model: claude-3-5-sonnet-20241022
  max-tokens: 2048
```

### Step 2: DTO 클래스 작성

```java
// server/src/main/java/com/gamepaper/claude/dto/AnalyzeRequest.java
package com.gamepaper.claude.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 UI에서 받는 AI 분석 요청 DTO.
 */
@Getter
@RequiredArgsConstructor
public class AnalyzeRequest {
    private final String url;
    private final Long gameId; // nullable (신규 등록 시 null)
}
```

```java
// server/src/main/java/com/gamepaper/claude/dto/AnalyzeResponse.java
package com.gamepaper.claude.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 분석 결과 응답 DTO.
 */
@Getter
@RequiredArgsConstructor
public class AnalyzeResponse {
    private final JsonNode strategy; // 파싱된 파싱 전략 JSON
    private final String rawJson;    // 원본 JSON 문자열
}
```

### Step 3: CrawlerStrategyParser 작성 (테스트 먼저)

```java
// server/src/test/java/com/gamepaper/claude/CrawlerStrategyParserTest.java
package com.gamepaper.claude;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CrawlerStrategyParserTest {

    CrawlerStrategyParser parser = new CrawlerStrategyParser();

    @Test
    void extractJson_정상_JSON_블록_파싱() {
        String response = "분석 결과입니다.\n```json\n{\"imageSelector\": \".wallpaper img\"}\n```";
        JsonNode result = parser.extractJson(response);
        assertThat(result.get("imageSelector").asText()).isEqualTo(".wallpaper img");
    }

    @Test
    void extractJson_코드블록_없이_직접_JSON() {
        String response = "{\"imageSelector\": \".img\", \"paginationType\": \"button_click\"}";
        JsonNode result = parser.extractJson(response);
        assertThat(result.get("paginationType").asText()).isEqualTo("button_click");
    }

    @Test
    void extractJson_JSON_없으면_예외() {
        assertThatThrownBy(() -> parser.extractJson("JSON이 없는 텍스트"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_필수필드_누락시_예외() {
        String json = "{\"paginationType\": \"button_click\"}"; // imageSelector 누락
        assertThatThrownBy(() -> parser.validateAndParse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageSelector");
    }
}
```

### Step 4: 테스트 실행 — 실패 확인

```bash
./gradlew test --tests "com.gamepaper.claude.CrawlerStrategyParserTest"
# CrawlerStrategyParser 클래스가 없으므로 컴파일 실패
```

### Step 5: CrawlerStrategyParser 구현

```java
// server/src/main/java/com/gamepaper/claude/CrawlerStrategyParser.java
package com.gamepaper.claude;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude API 응답 텍스트에서 파싱 전략 JSON을 추출하고 검증합니다.
 */
@Component
public class CrawlerStrategyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // ```json ... ``` 코드 블록 패턴
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.DOTALL);

    /**
     * 응답 텍스트에서 JSON 추출.
     * 1순위: ```json ... ``` 코드 블록
     * 2순위: 텍스트 전체를 JSON으로 시도
     */
    public JsonNode extractJson(String responseText) {
        Matcher m = JSON_BLOCK.matcher(responseText);
        if (m.find()) {
            return parseJson(m.group(1).trim());
        }
        // 코드 블록 없으면 전체 텍스트를 JSON으로 시도
        return parseJson(responseText.trim());
    }

    /**
     * JSON 문자열을 파싱하고 필수 필드를 검증.
     */
    public JsonNode validateAndParse(String jsonText) {
        JsonNode node = parseJson(jsonText);
        if (!node.has("imageSelector")) {
            throw new IllegalArgumentException("파싱 전략에 'imageSelector' 필드가 필요합니다.");
        }
        return node;
    }

    private JsonNode parseJson(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("유효하지 않은 JSON: " + e.getOriginalMessage());
        }
    }
}
```

### Step 6: 테스트 재실행 — PASS 확인

```bash
./gradlew test --tests "com.gamepaper.claude.CrawlerStrategyParserTest"
# 4개 테스트 모두 PASS
```

### Step 7: ClaudeApiClient 구현

```java
// server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java
package com.gamepaper.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gamepaper.claude.dto.AnalyzeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Claude API 클라이언트.
 * Spring Boot 3.2의 RestClient를 사용합니다.
 *
 * 사용 전제: ANTHROPIC_API_KEY 환경변수 설정 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
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

    /**
     * 주어진 HTML 소스를 분석하여 파싱 전략 JSON을 생성합니다.
     *
     * @param pageHtml 대상 페이지 HTML (헤더, 스크립트 태그 제거 권장)
     * @param pageUrl  분석 대상 URL (프롬프트 컨텍스트 제공용)
     * @return 파싱 전략 JSON과 원본 응답 텍스트
     * @throws IllegalStateException API 키가 설정되지 않은 경우
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
        // HTML에서 스크립트, 스타일 태그 제거하여 토큰 절약
        String cleanedHtml = html
                .replaceAll("(?s)<script[^>]*>.*?</script>", "")
                .replaceAll("(?s)<style[^>]*>.*?</style>", "");
        // 최대 8000자로 제한 (토큰 비용 절약)
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
              ]
            }

            JSON만 출력하세요 (```json 블록으로 감싸도 됩니다).
            """.formatted(url, cleanedHtml);
    }

    private String callApi(String prompt) {
        // Anthropic Messages API 요청 바디 구성
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

        String responseBody = RestClient.create()
                .post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        // 응답에서 텍스트 추출
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}
```

### Step 8: 커밋

```bash
git add server/src/main/java/com/gamepaper/claude/ \
        server/src/test/java/com/gamepaper/claude/ \
        server/src/main/resources/application-local.yml
git commit -m "feat: Claude API 클라이언트 및 파싱 전략 파서 구현 (Task 6)"
```

---

## Task 7: AI 분석 엔드포인트 + 폼 연결 완성

**Files:**
- Create: `server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java`
- Create: `server/src/test/java/com/gamepaper/admin/AdminAnalyzeApiControllerTest.java`

### Step 1: AdminAnalyzeApiController 테스트 작성

```java
// server/src/test/java/com/gamepaper/admin/AdminAnalyzeApiControllerTest.java
package com.gamepaper.admin;

import com.gamepaper.claude.ClaudeApiClient;
import com.gamepaper.claude.dto.AnalyzeResponse;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyzeApiController.class)
class AdminAnalyzeApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ClaudeApiClient claudeApiClient;
    @MockBean CrawlerStrategyRepository strategyRepository;

    @Test
    void analyze_정상요청_전략JSON_반환() throws Exception {
        // Mock 파싱 전략 JSON
        ObjectNode strategyNode = objectMapper.createObjectNode();
        strategyNode.put("imageSelector", ".wallpaper img");
        strategyNode.put("paginationType", "button_click");
        AnalyzeResponse mockResponse = new AnalyzeResponse(strategyNode, strategyNode.toString());

        when(claudeApiClient.analyzeHtml(anyString(), eq("https://example.com")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/admin/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy.imageSelector").value(".wallpaper img"));
    }

    @Test
    void analyze_URL_누락시_400() throws Exception {
        mockMvc.perform(post("/admin/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

### Step 2: 테스트 실행 — 실패 확인

```bash
./gradlew test --tests "com.gamepaper.admin.AdminAnalyzeApiControllerTest"
# AdminAnalyzeApiController 없으므로 실패
```

### Step 3: AdminAnalyzeApiController 구현

```java
// server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java
package com.gamepaper.admin;

import com.gamepaper.claude.ClaudeApiClient;
import com.gamepaper.claude.dto.AnalyzeRequest;
import com.gamepaper.claude.dto.AnalyzeResponse;
import com.gamepaper.domain.strategy.CrawlerStrategy;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * 관리자 UI AI 분석 REST API.
 * POST /admin/api/analyze — URL을 받아 HTML을 가져오고 Claude API로 파싱 전략 생성
 */
@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminAnalyzeApiController {

    private final ClaudeApiClient claudeApiClient;
    private final CrawlerStrategyRepository strategyRepository;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody AnalyzeRequest request) {
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL이 필요합니다."));
        }

        String pageUrl = request.getUrl().trim();
        log.info("AI 분석 시작 - url={}, gameId={}", pageUrl, request.getGameId());

        // 1. Jsoup으로 HTML 수집 (타임아웃 10초)
        String html;
        try {
            html = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (compatible; GamePaperBot)")
                    .timeout(10_000)
                    .get()
                    .html();
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
            // API 키 미설정
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
            // 기존 전략 버전 확인
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
                "preActions", java.util.List.of()
        );
    }
}
```

### Step 4: 테스트 재실행 — PASS 확인

```bash
./gradlew test --tests "com.gamepaper.admin.AdminAnalyzeApiControllerTest"
# 2개 테스트 PASS
```

### Step 5: 전체 테스트 실행

```bash
./gradlew test
# 모든 테스트 PASS 확인
```

### Step 6: 서버 실행 후 전체 UI 확인

```bash
# 환경변수 없이 실행 (데모 모드)
./gradlew bootRun

# 확인 순서:
# 1. http://localhost:8080/admin → 대시보드 카드 표시
# 2. http://localhost:8080/admin/games → 게임 테이블
# 3. http://localhost:8080/admin/games/new → 폼 입력
#    - URL 입력 후 "AI 분석 시작" 클릭 → 데모 전략 JSON 표시
#    - "저장" 클릭 → 상세 페이지 이동
# 4. http://localhost:8080/admin/games/{id} → 3개 탭 전환 확인
```

### Step 7: ANTHROPIC_API_KEY 실제 테스트 (선택)

```bash
# API 키가 있는 경우 실제 분석 테스트
ANTHROPIC_API_KEY=sk-ant-xxx ./gradlew bootRun
# /admin/games/new 에서 실제 게임 URL 입력 후 AI 분석 실행
```

### Step 8: flow.md 업데이트 및 최종 커밋

```bash
git add server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java \
        server/src/test/java/com/gamepaper/admin/AdminAnalyzeApiControllerTest.java
git commit -m "feat: AI 분석 API 엔드포인트 및 폼 폴링 연결 구현 (Task 7)"
```

---

## 의존성 추가 (build.gradle)

Task 6 구현 전에 `build.gradle`에 jackson-databind 확인 및 thymeleaf-extras 추가:

```groovy
// build.gradle dependencies에 추가
// Spring Boot starter-web에 jackson 포함되어 있으므로 별도 추가 불필요
// Thymeleaf extras (날짜 포맷팅용)
implementation 'org.thymeleaf.extras:thymeleaf-extras-java8time'
```

`build.gradle` 수정 후:

```bash
git add server/build.gradle
git commit -m "build: thymeleaf-extras-java8time 의존성 추가"
```

---

## 리스크 및 대응 방안

| 리스크 | 대응 방안 |
|--------|-----------|
| Claude API 키 미설정 | `AdminAnalyzeApiController`에서 API 키 없을 때 데모 전략 반환 — UI 테스트는 항상 가능 |
| Thymeleaf `#httpServletRequest` 사용 불가 | 레이아웃에서 `request` 객체 참조 대신 컨트롤러에서 `activeMenu` 모델 속성으로 대체 |
| `CrawlerScheduler.runSingle()` 동기 실행 블로킹 | `new Thread(...).start()`로 블로킹 방지 (Sprint 4에서 `@Async`로 교체) |
| Claude API 응답 JSON 형식 불일치 | `CrawlerStrategyParser.extractJson()`이 코드 블록/직접 JSON 모두 처리, 파싱 실패 시 예외 반환 |
| Jsoup으로 동적 페이지 HTML 수집 불가 | Task 7에서 Jsoup으로 1차 시도 → 실패 시 Sprint 4에서 Selenium으로 교체 |

---

## 품질 검증 체크리스트

- ⬜ ROADMAP.md의 S3-1, S3-2, S3-3 모든 항목 구현 완료
- ⬜ writing-plans 스킬 형식 준수 (bite-sized task, 전체 코드 포함)
- ⬜ 모든 Task가 개별 커밋으로 분리됨
- ⬜ `CrawlerStrategyParserTest` — 4개 테스트 PASS
- ⬜ `AdminAnalyzeApiControllerTest` — 2개 테스트 PASS
- ⬜ `LocalStorageServiceTest` — 3개 테스트 PASS
- ⬜ `/admin` 대시보드 브라우저 확인 완료
- ⬜ `/admin/games` 게임 목록 + 크롤링 버튼 동작 확인
- ⬜ `/admin/games/new` 폼 → 저장 → 상세 이동 확인
- ⬜ `/admin/games/{id}` 3개 탭 전환 확인
- ⬜ AI 분석 버튼 → JSON 표시 (데모 모드 또는 실제 API) 확인

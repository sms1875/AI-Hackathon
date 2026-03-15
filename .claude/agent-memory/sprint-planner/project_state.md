---
name: project_state
description: GamePaper 프로젝트의 현재 스프린트 진행 상태 및 완료된 스프린트 주요 달성 사항
type: project
---

# GamePaper 프로젝트 상태

현재 브랜치: `sprint4` (master에서 분기, 2026-03-15 생성)

## 완료된 스프린트

### Sprint 1 (완료: 2026-03-13)
- Spring Boot 3.x (Java 21) + SQLite + Docker Compose 기반 인프라 구축
- StorageService 추상화 + LocalStorageService 구현 (로컬 파일시스템 영구 URL)
- Game / Wallpaper / CrawlingLog JPA 엔티티 및 Repository 구현
- REST API: `GET /api/games`, `GET /api/wallpapers/{gameId}`
- 8개 테스트 통과, PR #1 생성 및 master 병합 완료

### Sprint 2 (완료: 2026-03-15)
- Phase 1 마일스톤(M1) 달성
- 6개 게임 크롤러 마이그레이션: GameCrawler 인터페이스 + CrawlerScheduler (6시간 주기)
- ImageProcessor (BlurHash + 해상도 추출)
- AbstractGameCrawler + Jsoup 크롤러 2개 (FFXIV, 검은사막)
- AbstractSeleniumCrawler + Selenium 크롤러 4개 (원신, 마비노기, 메이플스토리, NIKKE)
- Docker Compose Selenium standalone-chrome 서비스 + 게임 초기 데이터 (6개 게임)
- Flutter 앱 Firebase Storage → 서버 REST API 전환 (별도 레포)
- GitHub Actions CI (.github/workflows/ci.yml)
- PR #2 생성 및 master 병합 완료

### Sprint 3 (완료: 2026-03-15)
목표: Phase 2 시작 — 관리자 UI 프론트엔드(Thymeleaf) + Claude API 연동 기초
- Thymeleaf 공통 레이아웃 (Bootstrap 5 사이드바) + 대시보드
- 게임 목록/등록/상세 UI (3탭: 배경화면/전략/로그)
- CrawlerStrategy JPA 엔티티 + CrawlerStrategyRepository
- ClaudeApiClient (Spring RestClient), CrawlerStrategyParser
- AdminAnalyzeApiController: POST /admin/api/analyze (동기, API 키 미설정 시 데모 전략)
- HtmlFetcher (Jsoup, 테스트용 MockBean 분리)
- 22개 테스트 전체 PASS
- 코드 리뷰 이슈: I-1 new Thread() 사용, I-2 RestClient.create() 재생성, I-3 비활성화 FAILED 처리 부적절
- PR #3 생성 및 master 병합 완료

## 현재 스프린트

### Sprint 4 (계획 수립 완료: 2026-03-15)
목표: AI 파싱 전략 자동 생성 파이프라인 + GenericCrawlerExecutor 구현 (M2 달성)

브랜치: `sprint4`

**Task 1: Sprint 3 코드 리뷰 이슈 해소**
- GameStatus.INACTIVE 추가 (I-3)
- AppConfig에 @Async @EnableAsync + asyncExecutor 빈 (I-1)
- ClaudeApiClient RestClient.Builder 주입 (I-2)
- CrawlerScheduler.runSingleAsync(@Async) 추가
- AdminCrawlApiController new Thread() → runSingleAsync() 교체

**Task 2: AnalysisStatus + AnalysisService**
- AnalysisStatus 열거형: PENDING → ANALYZING → COMPLETED | FAILED
- Game 엔티티에 analysisStatus, analysisError 필드 추가
- AnalysisService @Async: HTML 수집 → Claude API → 전략 저장 파이프라인

**Task 3: AI 분석 API 재설계**
- POST /admin/games/{id}/analyze → 비동기 분석 트리거 (202 Accepted)
- GET /admin/games/{id}/analyze/status → 상태 polling (2초 간격)
- POST /admin/api/analyze → 하위 호환 (URL 기반 미리보기)

**Task 4: 프론트엔드 polling UI**
- game-new.html: 게임 저장 → 분석 트리거 → polling → 전략 미리보기
- game-detail.html: "재분석" 버튼 + polling → 새로고침
- game-list.html: analysisStatus 뱃지 추가

**Task 5: GenericCrawlerExecutor**
- StrategyDto (전략 JSON 역직렬화)
- GenericCrawlerExecutor: none/scroll/button_click/url_pattern 페이지네이션, preActions, stopCondition

**Task 6: AdminCrawlApiController GenericCrawlerExecutor 연결**
- 전략 있으면 GenericCrawlerExecutor, 없으면 기존 크롤러 fallback
- CrawlerScheduler.runGenericAsync() + runGeneric() 추가
- 연속 3회 실패 → GameStatus.FAILED 자동 전환
- CrawlingLogRepository.findTop3ByGameIdOrderByStartedAtDesc() 추가

**Task 7: DataInitializer — 기존 6개 게임 DB 등록**
- CommandLineRunner로 앱 시작 시 멱등성 보장하며 등록

**Task 8: CrawlerScheduler 전략 우선 실행**
- runAll(): 전략 있는 게임 → GenericCrawlerExecutor, 없는 게임 → 기존 크롤러 fallback
- GameStatus.INACTIVE 게임 건너뜀

**Task 9: 전체 빌드 검증 + deploy.md**

**Task 10: 기존 크롤러 클래스 제거 (검증 후)**

**Why:** Phase 2 M2 마일스톤 — URL 등록만으로 AI 파싱 전략 생성 + GenericCrawlerExecutor 크롤링 구현.

## 주요 기술 스택 및 패키지 구조

```
com.gamepaper
├── admin/          # Thymeleaf 컨트롤러
│   ├── AdminDashboardController
│   ├── AdminGameController
│   ├── AdminCrawlApiController
│   ├── AdminAnalyzeApiController  ← Sprint 4에서 재설계
│   └── dto/
├── api/            # REST API 컨트롤러
├── claude/         # Claude API
│   ├── ClaudeApiClient
│   ├── CrawlerStrategyParser
│   ├── AnalysisService            ← Sprint 4 신설
│   ├── HtmlFetcher
│   └── dto/
├── config/
│   ├── AppConfig                  ← Sprint 4에서 @EnableAsync 추가
│   └── DataInitializer            ← Sprint 4 신설
├── crawler/
│   ├── AbstractGameCrawler
│   ├── CrawlerScheduler           ← Sprint 4에서 GenericCrawlerExecutor 연동
│   ├── generic/
│   │   ├── StrategyDto            ← Sprint 4 신설
│   │   └── GenericCrawlerExecutor ← Sprint 4 신설
│   ├── selenium/                  ← Sprint 4 Task 10에서 제거 예정
│   └── jsoup/                     ← Sprint 4 Task 10에서 제거 예정
├── domain/
│   ├── game/
│   │   ├── Game                   ← analysisStatus, analysisError 필드 추가
│   │   ├── GameStatus             ← INACTIVE 추가
│   │   └── AnalysisStatus         ← Sprint 4 신설
│   ├── strategy/
│   └── wallpaper/
└── storage/
```

## 환경 변수

| 변수명 | 용도 | 기본값 |
|--------|------|--------|
| ANTHROPIC_API_KEY | Claude API 인증 | 미설정 시 데모 모드 |
| STORAGE_ROOT | 파일 저장 경로 | /app/storage |
| BASE_URL | 이미지 서빙 기본 URL | http://localhost:8080 |
| SELENIUM_HUB_URL | Selenium 허브 | http://localhost:4444 |

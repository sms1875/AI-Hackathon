# GamePaper AI — 서버

Spring Boot 3.2 (Java 21) 기반 백엔드 서버입니다. SQLite DB, 로컬 파일 스토리지, Selenium 크롤러, Claude AI 연동을 포함합니다.

---

## 서버 아키텍처

```
클라이언트 (Flutter 앱)
        ↕ REST API (:8080)
Spring Boot 서버
    ├── API 레이어        ← GameApiController, WallpaperApiController, TagApiController,
    │                       WallpaperLikeApiController, WallpaperRecommendApiController
    ├── 관리자 UI         ← AdminDashboardController, AdminGameController (Thymeleaf)
    ├── 서비스 레이어     ← WallpaperSearchService, RecommendationService
    ├── AI 연동           ← ClaudeApiClient, AnalysisService, TaggingService, BatchTaggingService
    ├── 크롤러           ← CrawlerScheduler, GenericCrawlerExecutor, ImageProcessor
    ├── 도메인 레이어     ← JPA 엔티티 (Game, Wallpaper, CrawlerStrategy, CrawlingLog, UserLike)
    └── 스토리지 레이어   ← StorageService (인터페이스) → LocalStorageService
            ↕
SQLite (gamepaper.db)            ← 게임/배경화면/전략/좋아요 데이터
로컬 파일 시스템 (storage/images/) ← 크롤링된 이미지
Selenium standalone-chrome        ← 동적 페이지 크롤링
Claude API (Anthropic)            ← 파싱 전략 생성, 이미지 태그 생성
```

---

## 패키지 구조

```
src/main/java/com/gamepaper/
├── admin/                          관리자 UI 컨트롤러
│   ├── AdminDashboardController    GET /admin — 대시보드
│   ├── AdminGameController         GET/POST /admin/games — 게임 목록/등록/상세
│   ├── AdminCrawlApiController     POST /admin/api/games/{id}/crawl — 수동 크롤링
│   ├── AdminAnalyzeApiController   POST/GET /admin/games/{id}/analyze — AI 분석 트리거/폴링
│   ├── AdminTaggingApiController   POST /admin/api/games/{id}/tagging — 일괄 태깅
│   └── dto/                        DashboardData, GameListItem
│
├── api/                            클라이언트용 REST API
│   ├── GameApiController           GET /api/games
│   ├── WallpaperApiController      GET /api/wallpapers/{gameId}
│   ├── WallpaperLikeApiController  POST /api/wallpapers/{id}/like
│   ├── WallpaperRecommendApiController  GET /api/wallpapers/recommended
│   ├── TagApiController            GET /api/tags
│   ├── WallpaperSearchService      AND/OR 태그 검색, 태그 빈도 분석
│   ├── RecommendationService       좋아요 이력 기반 추천
│   └── error/                      GlobalExceptionHandler, ErrorCode, ErrorResponse
│
├── claude/                         Claude AI 연동
│   ├── ClaudeApiClient             REST API 호출 (전략 생성, Vision 태그 생성)
│   ├── CrawlerStrategyParser       응답 JSON 파싱 + 필수 필드 검증
│   ├── HtmlFetcher                 Jsoup HTML 수집 (분석 전 단계)
│   ├── AnalysisService             비동기 AI 분석 파이프라인 (@Async)
│   ├── TaggingService              이미지 단건 자동 태그 생성 (Claude Vision)
│   ├── BatchTaggingService         기존 이미지 일괄 태그 생성
│   └── dto/                        AnalyzeRequest, AnalyzeResponse
│
├── config/                         Spring 설정
│   ├── AppConfig                   @EnableAsync + asyncExecutor 스레드풀
│   ├── DatabaseConfig              SQLite WAL 모드 활성화
│   ├── DataInitializer             앱 시작 시 6개 게임 초기 데이터 등록 (멱등성)
│   ├── StorageConfig               이미지 정적 서빙 경로 설정
│   └── WebConfig                   CORS 설정
│
├── crawler/                        크롤링 엔진
│   ├── GameCrawler                 크롤러 인터페이스
│   ├── AbstractGameCrawler         공통 로직 (저장, 중복 체크, 로그)
│   ├── CrawlerScheduler            6시간 주기 자동 실행, 수동 트리거
│   ├── CrawlResult                 크롤링 결과 VO
│   ├── generic/
│   │   ├── GenericCrawlerExecutor  전략 JSON 기반 범용 크롤러 (4가지 페이지네이션)
│   │   └── StrategyDto             파싱 전략 역직렬화 DTO
│   ├── image/
│   │   ├── ImageProcessor          이미지 다운로드, BlurHash 생성, UUID 파일명, 해상도 추출
│   │   └── ImageMetadata           이미지 메타데이터 VO
│   └── selenium/
│       └── AbstractSeleniumCrawler Selenium WebDriver 공통 추상화
│
├── domain/                         JPA 엔티티 및 Repository
│   ├── game/
│   │   ├── Game                    게임 엔티티 (name, url, status, analysisStatus)
│   │   ├── GameRepository          JPA Repository
│   │   ├── GameStatus              ACTIVE / INACTIVE / FAILED
│   │   └── AnalysisStatus          PENDING / ANALYZING / COMPLETED / FAILED
│   ├── wallpaper/
│   │   ├── Wallpaper               배경화면 엔티티 (url, blurHash, tags, width, height)
│   │   └── WallpaperRepository     JPA Repository (태그 검색, 페이징, 일괄 조회)
│   ├── strategy/
│   │   ├── CrawlerStrategy         파싱 전략 엔티티 (strategyJson, version, analyzedAt)
│   │   └── CrawlerStrategyRepository  최신 버전 조회, 이력 전체 조회
│   ├── crawler/
│   │   ├── CrawlingLog             크롤링 로그 엔티티 (startedAt, finishedAt, count, status, error)
│   │   ├── CrawlingLogRepository   최근 로그 조회
│   │   └── CrawlingLogStatus       SUCCESS / FAILED / RUNNING
│   └── like/
│       ├── UserLike                좋아요 엔티티 (deviceId, wallpaperId)
│       └── UserLikeRepository      기기별 좋아요 이력 조회
│
├── storage/                        스토리지 추상화
│   ├── StorageService              upload(), getUrl(), delete(), download(), listFiles()
│   └── local/
│       └── LocalStorageService     로컬 파일 시스템 구현체 (HTTP 직접 서빙)
│
└── GamepaperApplication            Spring Boot 엔트리포인트
```

---

## 환경변수 목록

`server/.env` 파일로 관리합니다. `server/.env.example` 참고.

| 환경변수 | 기본값 | 필수 | 설명 |
|----------|--------|------|------|
| `ANTHROPIC_API_KEY` | (없음) | 아니오 | Claude API 키. 미설정 시 AI 분석은 데모 전략 반환, 태그 생성 건너뜀 |
| `SELENIUM_HUB_URL` | `http://selenium:4444` | 아니오 | Selenium Grid URL. Docker Compose 내부 통신 시 기본값 사용 |
| `CRAWLER_SCHEDULE_DELAY_MS` | `21600000` | 아니오 | 크롤링 주기 (ms). 테스트 시 `60000`(1분)으로 변경 |
| `STORAGE_ROOT` | `/app/storage` | 아니오 | 이미지 저장 루트 경로 |
| `BASE_URL` | `http://localhost:8080` | 아니오 | 이미지 URL prefix (클라이언트에 반환되는 URL 기준) |

`application-local.yml` 추가 설정:

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `claude.model` | `claude-3-5-sonnet-20241022` | Claude 모델 ID |
| `claude.max-tokens` | `2048` | 최대 응답 토큰 수 |

---

## 로컬 실행 방법

### Docker Compose (권장)

```bash
cd server/

# 사전 준비 (최초 1회)
touch gamepaper.db           # Windows: type nul > gamepaper.db
mkdir -p storage/images
cp .env.example .env         # ANTHROPIC_API_KEY 선택적 입력

# 빌드 및 실행
docker compose up --build

# 기동 확인 (약 60~90초 후)
curl http://localhost:8080/api/games
```

컨테이너 구성:
- `backend`: Spring Boot 서버 (`:8080`)
- `selenium`: Selenium standalone-chrome (`:4444`, VNC `:7900`)

### 직접 실행 (로컬 Java 환경)

Selenium은 별도로 실행되어야 합니다.

```bash
cd server/

# Selenium Hub 별도 실행 (Docker)
docker run -d -p 4444:4444 --shm-size=2g selenium/standalone-chrome:latest

# Spring Boot 직접 실행 (Java 21 필요)
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 테스트 실행

```bash
cd server/

# 전체 테스트 (56개)
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "com.gamepaper.api.WallpaperSearchServiceTest"

# 빌드 검증만
./gradlew compileJava compileTestJava

# Windows 환경
gradlew.bat test
```

### 테스트 커버리지

| 테스트 클래스 | 개수 | 내용 |
|------------|------|------|
| `WallpaperSearchServiceTest` | 8 | AND/OR 검색, JSON 파싱, 태그 빈도 분석 |
| `RecommendationServiceTest` | 4 | 좋아요 이력 없음/있음, 중복 제외 |
| `WallpaperApiControllerTest` | 4 | 페이지 조회, 404, 페이지 파라미터 |
| `ClaudeApiClientTest` | 2 | API 키 미설정 예외 |
| `ErrorResponseTest` | 3 | 404/400 구조화 에러 응답 |
| `AdminAnalyzeApiControllerTest` | 2 | AI 분석 API, 데모 모드 |
| `GameApiControllerTest` | 3 | 게임 목록 API |
| `CrawlerStrategyParserTest` | 4 | JSON 코드 블록 추출, 필수 필드 검증 |
| `ImageProcessorTest` | 5 | BlurHash, 해상도, UUID 파일명, 확장자 |
| `GameRepositoryTest` | 3 | 저장/조회, 상태별 필터 |
| `LocalStorageServiceTest` | 7 | 업로드, 삭제, URL, 파일 목록, 빈 디렉토리 |
| `BatchTaggingServiceTest` | 2 | API 키 미설정 동작 |
| `TaggingServiceTest` | 2 | 태그 생성 서비스 |
| `WallpaperSearchApiTest` | 3 | 검색 API MockMvc |
| `LikeApiTest` | 4 | 좋아요 토글 API |
| **합계** | **56** | |

---

## CI/CD

GitHub Actions 파이프라인: `.github/workflows/ci.yml`

- 트리거: `master` 브랜치 PR 또는 push
- 단계: `./gradlew compileJava compileTestJava test`
- 상태: https://github.com/sms1875/AI-Hackathon/actions

---

## 주요 설계 결정

- **StorageService 추상화**: `LocalStorageService` (Phase 1) / `CloudStorageService` (Phase 2+) 구현체를 Spring Profile로 자동 선택. 클라우드 전환 시 코드 변경 없음.
- **JPA ddl-auto=update**: 엔티티 변경 시 자동으로 스키마 반영. 별도 마이그레이션 불필요.
- **데모 모드**: `ANTHROPIC_API_KEY` 미설정 시 AI 분석은 고정 데모 전략 반환, 태그 생성은 건너뜀. 로컬 개발 환경에서 API 키 없이도 전체 기능 테스트 가능.
- **GenericCrawlerExecutor**: 게임별 크롤러 클래스 6개 → 전략 JSON 기반 범용 크롤러 1개. 신규 게임 추가 시 DB에 URL만 등록하면 됨.
- **@Async 비동기**: AI 분석은 최대 60초 소요. `@Async("asyncExecutor")`로 별도 스레드풀에서 실행하여 API 응답 블로킹 방지.

# GamePaper AI

게임 공식 사이트의 배경화면을 AI로 자동 수집하여 모바일 기기에 적용하는 앱입니다.

---

## 프로젝트 소개

**GamePaper AI**는 기존 [GamePaper](https://github.com/sms1875/GamePaper) 앱을 리팩토링하고 AI 기능을 추가한 프로젝트입니다. 게임 URL만 등록하면 Claude AI가 페이지를 분석하여 파싱 전략을 자동 생성하고, 크롤링부터 이미지 태그 생성, 좋아요 기반 추천까지 전 과정을 자동화합니다.

### 주요 기능

- **AI 범용 크롤러**: 게임 URL 입력 → Claude AI가 HTML 분석 → 파싱 전략(JSON) 자동 생성 → 6시간 주기 자동 크롤링
- **자동 태그 생성**: 크롤링한 배경화면 이미지를 Claude Vision API로 분석하여 태그 자동 부여 (`dark`, `landscape`, `character` 등)
- **태그 기반 검색**: AND/OR 모드 태그 검색으로 원하는 배경화면 필터링
- **AI 추천**: 사용자 좋아요 이력 기반으로 유사한 배경화면 추천
- **관리자 대시보드**: 게임 등록·관리, 크롤링 상태 모니터링, AI 분석 트리거
- **구조화된 에러 처리**: 서버 표준 에러 응답(`ErrorCode`) + Flutter 클라이언트 `AppError` 매핑
- **영속 캐시**: Flutter 앱에서 SharedPreferences 기반 TTL 캐시 (게임 목록 1시간, 배경화면 페이지별)
- **무한 스크롤**: 배경화면 GridView + ScrollController 기반 페이지 추가 로드

---

## 아키텍처 개요

```
Flutter 앱 (Android)
    ↕ HTTP REST API
Spring Boot 서버 (Docker)
    ├── SQLite (gamepaper.db)         ← 게임/배경화면/전략/좋아요 데이터
    ├── 로컬 파일 스토리지 (/storage/images/)  ← 크롤링된 이미지
    ├── Selenium (standalone-chrome)  ← 동적 페이지 크롤링
    └── Claude API (Anthropic)        ← 파싱 전략 생성, 이미지 태그 생성
```

**추상화 레이어**:
- `StorageService` 인터페이스 → `LocalStorageService` (Phase 1), `CloudStorageService` (Phase 2+)
- Spring Profile (`local` / `prod`) 로 구현체 자동 선택

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 모바일 앱 | Flutter (Dart), Provider, cached_network_image, flutter_blurhash |
| 백엔드 | Spring Boot 3.2 (Java 21), Spring Data JPA, Thymeleaf |
| 크롤링 | Selenium 4.18 (standalone-chrome) + Jsoup 1.17 |
| AI | Claude API (claude-3-5-sonnet-20241022), Vision API |
| DB | SQLite 3.45 (Hibernate SQLiteDialect) |
| 스토리지 | 로컬 파일 시스템 (Docker 볼륨 마운트) |
| 이미지 처리 | BlurHash (썸네일 placeholder), WebP |
| 배포 | Docker Compose, GitHub Actions CI |

---

## 구현된 기능 목록

### 서버

| 기능 | 상태 | Sprint |
|------|------|--------|
| StorageService / LocalStorageService 추상화 | ✅ | Sprint 1 |
| GameRepository / WallpaperRepository (SQLite) | ✅ | Sprint 1 |
| Docker Compose (backend + selenium) | ✅ | Sprint 1-2 |
| ImageProcessor (BlurHash, UUID 파일명, 해상도 추출) | ✅ | Sprint 2 |
| CrawlerScheduler (6시간 자동 크롤링) | ✅ | Sprint 2 |
| 관리자 대시보드 UI (Thymeleaf 4페이지) | ✅ | Sprint 3 |
| ClaudeApiClient (HTML + 스크린샷 → 파싱 전략) | ✅ | Sprint 3 |
| CrawlerStrategy 엔티티 + 버전 관리 | ✅ | Sprint 3 |
| AnalysisService 비동기 AI 분석 파이프라인 | ✅ | Sprint 4 |
| GenericCrawlerExecutor (4가지 페이지네이션 타입) | ✅ | Sprint 4 |
| DataInitializer (6개 게임 초기 데이터 자동 등록) | ✅ | Sprint 4 |
| TaggingService (Claude Vision API 자동 태그) | ✅ | Sprint 5 |
| BatchTaggingService (기존 이미지 일괄 태깅) | ✅ | Sprint 5 |
| WallpaperSearchService (AND/OR 태그 검색) | ✅ | Sprint 5 |
| UserLike 엔티티 + 좋아요 토글 API | ✅ | Sprint 5 |
| RecommendationService (좋아요 이력 기반 추천) | ✅ | Sprint 5 |
| GlobalExceptionHandler + ErrorCode 표준 에러 응답 | ✅ | Sprint 6 |
| 컨트롤러 분리 (Like / Recommend 분리) | ✅ | Sprint 6 |

### Flutter 클라이언트

| 기능 | 상태 | Sprint |
|------|------|--------|
| Firebase Storage → 서버 REST API 전환 | ✅ | Sprint 2 |
| 게임 목록 화면, 배경화면 그리드 | ✅ | Sprint 2 |
| TagFilterChips (태그 필터 UI) | ✅ | Sprint 5 |
| RecommendedSection (추천 배경화면 섹션) | ✅ | Sprint 5 |
| WallpaperCard 좋아요 버튼 | ✅ | Sprint 5 |
| AppError 구조화 에러 처리 + 서버 에러 코드 매핑 | ✅ | Sprint 6 |
| LocalCache SharedPreferences TTL 기반 영속 캐시 | ✅ | Sprint 6 |
| WallpaperProvider 무한 스크롤 | ✅ | Sprint 6 |
| 오프라인 배너 표시 | ✅ | Sprint 6 |

### 보류 항목 (Phase 4)

| 기능 | 상태 |
|------|------|
| Flutter 데스크탑 지원 (Windows/macOS/Linux) | ⏸️ 보류 |
| 해상도별 이미지 추천 | ⏸️ 보류 |
| 자연어 검색 | ⏸️ 보류 |
| 라이브 배경화면 | ⏸️ 보류 |

---

## 빠른 시작 가이드

### Prerequisites

- Java 21 (Gradle 빌드용, 로컬 직접 실행 시)
- Docker Desktop (서버 실행용)
- Flutter SDK 3.x+ (앱 빌드용)
- Android SDK / 에뮬레이터 (앱 실행용)
- `ANTHROPIC_API_KEY` — AI 분석 및 자동 태그 생성에 필요 (없으면 데모 모드로 동작)

### 1. 서버 실행

```bash
cd server/

# 최초 실행 전 사전 준비 (반드시 실행)
touch gamepaper.db           # Windows: type nul > gamepaper.db
mkdir -p storage/images

# 환경변수 파일 생성
cp .env.example .env
# .env 파일에 ANTHROPIC_API_KEY=sk-ant-... 추가 (선택)

# 서버 빌드 및 실행 (최초 약 3~5분 소요)
docker compose up --build
```

서버 기동 후 60~90초 내에 `Started GamepaperApplication` 로그가 출력됩니다. Selenium 컨테이너 헬스체크 통과 후 백엔드가 기동됩니다.

**동작 확인:**
```bash
curl http://localhost:8080/api/games
# 예상: 6개 게임 목록 반환 (원신, 마비노기, 메이플스토리 모바일, NIKKE, FFXIV, 검은사막)
```

### 2. 관리자 UI 접속

브라우저에서 `http://localhost:8080/admin` 접속

### 3. Flutter 앱 실행

```bash
cd /path/to/GamePaper/client

# 의존성 설치
flutter pub get

# 서버 IP 설정 (lib/config/api_config.dart)
# Android 에뮬레이터: 10.0.2.2:8080
# 실기기: 로컬 네트워크 IP (예: 192.168.x.x:8080)

# 앱 실행
flutter run
```

---

## API 엔드포인트 목록

### 클라이언트용

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/games` | 게임 목록 조회 |
| GET | `/api/wallpapers/{gameId}?page=0&size=12` | 게임별 배경화면 페이징 조회 |
| GET | `/api/wallpapers/search?tags=dark&mode=and` | 태그 AND/OR 검색 |
| GET | `/api/wallpapers/recommended` | 좋아요 이력 기반 추천 (`device-id` 헤더 필요) |
| POST | `/api/wallpapers/{id}/like` | 좋아요 토글 (`device-id` 헤더 필요) |
| GET | `/api/tags` | 태그 목록 (빈도순) |

### 관리자용 (Admin UI)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/admin` | 대시보드 |
| GET | `/admin/games` | 게임 목록 + 크롤러 상태 |
| GET | `/admin/games/new` | 게임 등록 폼 |
| POST | `/admin/games` | 게임 등록 |
| GET | `/admin/games/{id}` | 게임 상세 (배경화면/전략/로그 탭) |
| POST | `/admin/games/{id}/analyze` | AI 분석 트리거 (202 Accepted) |
| GET | `/admin/games/{id}/analyze/status` | AI 분석 진행 상태 폴링 |
| PUT | `/admin/games/{id}/toggle` | 게임 활성화/비활성화 |
| DELETE | `/admin/games/{id}` | 게임 삭제 |

### 관리자용 (API)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/admin/api/analyze` | URL → 파싱 전략 즉시 생성 (데모 모드 지원) |
| POST | `/admin/api/games/{id}/crawl` | 수동 크롤링 실행 |
| POST | `/admin/api/games/{id}/tagging` | 기존 이미지 일괄 태깅 |

### 이미지 서빙

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/storage/images/{gameId}/{filename}` | 크롤링된 이미지 직접 서빙 |

---

## 환경변수 설명

서버 실행 시 `server/.env` 파일로 관리합니다.

| 환경변수 | 기본값 | 설명 |
|----------|--------|------|
| `ANTHROPIC_API_KEY` | (없음) | Claude API 키. 미설정 시 AI 분석은 데모 전략 반환, 태그 생성 건너뜀 |
| `SELENIUM_HUB_URL` | `http://selenium:4444` | Selenium Grid URL (Docker 내부 통신) |
| `CRAWLER_SCHEDULE_DELAY_MS` | `21600000` | 크롤링 주기 (ms). 테스트 시 `60000`(1분)으로 변경 |
| `STORAGE_ROOT` | `/app/storage` | 이미지 저장 루트 경로 |
| `BASE_URL` | `http://localhost:8080` | 이미지 URL prefix (클라이언트에 반환되는 URL 기준) |

`.env` 파일 예시:
```
ANTHROPIC_API_KEY=sk-ant-api03-...
CRAWLER_SCHEDULE_DELAY_MS=21600000
```

---

## 테스트 실행 방법

### 서버 테스트

```bash
cd server/

# 전체 테스트 실행 (50개)
./gradlew test

# Windows 환경
gradlew.bat test
```

주요 테스트 클래스:

| 테스트 클래스 | 개수 | 내용 |
|------------|------|------|
| `WallpaperSearchServiceTest` | 8 | AND/OR 검색, JSON 파싱, 태그 빈도 분석 |
| `RecommendationServiceTest` | 4 | 좋아요 이력, 태그 기반 추천, 중복 제외 |
| `WallpaperApiControllerTest` | 3 | 페이지 조회, 404 에러, 페이지 파라미터 |
| `ClaudeApiClientTest` | 2 | API 키 미설정 예외 검증 |
| `ErrorResponseTest` | 2 | 구조화된 에러 응답 포맷 검증 |
| `AdminAnalyzeApiControllerTest` | 2 | AI 분석 API, 데모 모드 |
| `GameApiControllerTest` | 2 | 게임 목록 API |
| `CrawlerStrategyParserTest` | 4 | JSON 파싱, 필수 필드 검증 |
| `ImageProcessorTest` | 5 | BlurHash 생성, 해상도 추출, UUID 파일명 |
| `GameRepositoryTest` | 2 | 저장/조회, 상태별 조회 |
| `LocalStorageServiceTest` | 7 | 업로드, 삭제, URL 반환, 파일 목록 |

### Flutter 코드 분석

```bash
cd /path/to/GamePaper/client
flutter analyze
```

---

## 프로젝트 구조

```
AI-Hackathon/                      ← 이 레포지토리 (서버 + 문서)
├── server/
│   ├── src/main/java/com/gamepaper/
│   │   ├── admin/                 ← 관리자 UI 컨트롤러 (Dashboard, Game, Crawl, Tagging)
│   │   ├── api/                   ← 클라이언트 API (Game, Wallpaper, Tag, Like, Recommend)
│   │   │   └── error/             ← ErrorCode, ErrorResponse, GlobalExceptionHandler
│   │   ├── claude/                ← Claude API 클라이언트, 분석/태깅 서비스
│   │   ├── config/                ← AppConfig, DataInitializer, StorageConfig
│   │   ├── crawler/               ← GameCrawler 인터페이스, CrawlerScheduler, GenericCrawlerExecutor
│   │   │   ├── generic/           ← 전략 기반 범용 크롤러
│   │   │   ├── image/             ← ImageProcessor (BlurHash, 해상도)
│   │   │   └── selenium/          ← AbstractSeleniumCrawler
│   │   ├── domain/                ← JPA 엔티티 및 Repository
│   │   │   ├── game/              ← Game, GameStatus, AnalysisStatus
│   │   │   ├── wallpaper/         ← Wallpaper, WallpaperRepository
│   │   │   ├── strategy/          ← CrawlerStrategy (파싱 전략 버전 관리)
│   │   │   ├── crawler/           ← CrawlingLog, CrawlingLogStatus
│   │   │   └── like/              ← UserLike (기기별 좋아요)
│   │   └── storage/               ← StorageService 인터페이스, LocalStorageService
│   ├── src/main/resources/
│   │   ├── templates/admin/       ← Thymeleaf 관리자 UI (dashboard, game-list, game-new, game-detail)
│   │   ├── application.yml        ← 공통 설정
│   │   └── application-local.yml  ← SQLite, 로컬 스토리지, Claude API 설정
│   ├── docker-compose.yml         ← backend + selenium 서비스 정의
│   └── Dockerfile
├── docs/
│   ├── PRD.md                     ← 제품 요구사항 문서
│   ├── ROADMAP.md                 ← 4 Phase, 8 Sprint 로드맵
│   ├── TECH-SPEC.md               ← 기술 사양서
│   ├── DATA-FLOW.md               ← 데이터 흐름도
│   ├── deploy.md                  ← 배포 및 검증 가이드
│   ├── flow.md                    ← 작업 이력
│   └── sprint/                    ← 스프린트 계획 및 검증 보고서
└── .claude/
    ├── agents/                    ← Claude Code 에이전트 정의
    └── skills/                    ← Claude Code 스킬 정의

GamePaper/client/                  ← Flutter 앱 (별도 레포)
├── lib/
│   ├── cache/                     ← LocalCache (SharedPreferences TTL)
│   ├── config/                    ← ApiConfig (서버 URL 설정)
│   ├── errors/                    ← AppError, AppErrorType
│   ├── models/                    ← Game, Wallpaper (tags, likeCount 포함)
│   ├── providers/                 ← HomeProvider, WallpaperProvider, TagFilterProvider, RecommendationProvider
│   ├── repositories/              ← GameRepository (API 호출, 캐시 포함)
│   ├── screens/                   ← HomeScreen, WallpaperScreen
│   ├── utils/                     ← DeviceId (SharedPreferences 기반)
│   └── widgets/                   ← TagFilterChips, RecommendedSection, WallpaperCard
└── pubspec.yaml
```

---

## 개발 로드맵

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 기초 (DB/스토리지 추상화, Docker) | ✅ 완료 (Sprint 1-2) |
| Phase 2 | AI 범용 크롤러 + 관리자 페이지 + 태그/추천 | ✅ 완료 (Sprint 3-5) |
| Phase 3 | 리팩토링 (캐시, 에러 처리, 테스트) | ✅ 완료 (Sprint 6) |
| Phase 4 | 멀티플랫폼 확장 (데스크탑, 해상도별 추천) | ⏸️ 보류 |

전체 로드맵: [docs/ROADMAP.md](docs/ROADMAP.md)

---

## 문서

| 문서 | 설명 |
|------|------|
| [PRD](docs/PRD.md) | 제품 요구사항 문서 (기능, AS-IS/TO-BE, API 설계) |
| [ROADMAP](docs/ROADMAP.md) | 4 Phase, 8 Sprint 개발 로드맵 |
| [TECH-SPEC](docs/TECH-SPEC.md) | 기술 사양서 (패키지 구조, DB 스키마, API 스펙) |
| [DATA-FLOW](docs/DATA-FLOW.md) | 데이터 흐름 (크롤링 파이프라인, AI 분석, 이미지 서빙) |
| [deploy.md](docs/deploy.md) | 배포 및 검증 가이드 |
| [server/README.md](server/README.md) | 서버 전용 가이드 |
| [flow.md](docs/flow.md) | 작업 이력 |

---

## Claude Code 설정

이 레포지토리는 Claude Code 에이전트/스킬을 포함합니다.

| 에이전트/스킬 | 역할 |
|--------------|------|
| `prd-to-roadmap` | PRD → ROADMAP 자동 생성 |
| `sprint-planner` | 스프린트 계획 수립 |
| `sprint-close` | 스프린트 마무리 (PR, 코드 리뷰, 검증) |
| `code-reviewer` | 코드 리뷰 (Critical/Important/Suggestion) |
| `writing-plans` | 구현 전 단계별 계획 작성 |
| `karpathy-guidelines` | LLM 코딩 실수 방지 가이드라인 |

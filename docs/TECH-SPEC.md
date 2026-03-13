# 기술 사양서 (Technical Specification)

> 프로젝트: GamePaper
> 기반 문서: PRD.md, ROADMAP.md

---

## 1. 시스템 아키텍처 개요

```
[Flutter 앱 (Android/Desktop)]
        ↕ REST API (HTTP)
[Spring Boot 서버]
  ├── Admin UI (Thymeleaf)
  ├── REST API Controller
  ├── AI 분석 서비스 (Claude API)
  ├── GenericCrawlerExecutor
  │     └── Selenium (standalone-chrome)
  ├── StorageService (interface)
  │     └── LocalStorageService → /storage/images/{gameId}/
  └── Repository (interface)
        └── JPA → SQLite (gamepaper.db)
```

---

## 2. 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| 백엔드 언어 | Java | 21 |
| 백엔드 프레임워크 | Spring Boot | 3.x |
| 빌드 도구 | Gradle | 최신 |
| DB (Phase 1) | SQLite | - |
| ORM | Spring Data JPA + Hibernate | - |
| DB 마이그레이션 | Flyway (안정화 후 전환) / JPA auto-ddl (초기) | - |
| 크롤링 | Selenium 4.x + Jsoup 1.x | - |
| AI | Claude API (Anthropic) | claude-3-5-sonnet |
| 서버 사이드 렌더링 | Thymeleaf | - |
| 컨테이너 | Docker Compose | - |
| 모바일 앱 | Flutter (Dart) | - |
| 상태관리 | Provider | - |
| 로컬 캐시 | Hive | Phase 3 |
| CI | GitHub Actions | - |

---

## 3. 프로젝트 패키지 구조

```
com.gamepaper
├── config/
│   ├── AppConfig.java              # 빈 설정, 비동기 설정
│   ├── StorageConfig.java          # 정적 파일 서빙 경로 등록
│   └── WebConfig.java              # CORS, MVC 설정
├── domain/
│   ├── game/
│   │   ├── Game.java               # 엔티티
│   │   ├── GameRepository.java     # JPA Repository
│   │   ├── GameService.java        # 비즈니스 로직
│   │   └── GameStatus.java         # enum: ACTIVE, UPDATING, FAILED
│   ├── wallpaper/
│   │   ├── Wallpaper.java
│   │   ├── WallpaperRepository.java
│   │   └── WallpaperService.java
│   ├── crawler/
│   │   ├── CrawlerStrategy.java    # 파싱 전략 엔티티
│   │   ├── CrawlerStrategyRepository.java
│   │   ├── CrawlingLog.java        # 크롤링 이력 엔티티
│   │   └── CrawlingLogRepository.java
│   └── like/
│       ├── UserLike.java           # Phase 5
│       └── UserLikeRepository.java
├── storage/
│   ├── StorageService.java         # 인터페이스
│   └── local/
│       └── LocalStorageService.java
├── crawler/
│   ├── GenericCrawlerExecutor.java # 범용 크롤러 실행기
│   ├── CrawlerScheduler.java       # @Scheduled 6시간 주기
│   └── strategy/
│       ├── ParseStrategy.java      # JSON → 파싱 전략 DTO
│       └── StrategyValidator.java  # JSON 스키마 검증
├── ai/
│   ├── ClaudeApiClient.java        # Claude HTTP 클라이언트
│   ├── AnalysisService.java        # AI 분석 비즈니스 로직 (비동기)
│   └── prompt/
│       └── StrategyPromptBuilder.java  # 프롬프트 템플릿 생성
├── api/
│   ├── GameApiController.java      # GET /api/games
│   ├── WallpaperApiController.java # GET /api/wallpapers/**
│   └── dto/
│       ├── GameDto.java
│       ├── WallpaperDto.java
│       └── PagedResponse.java
└── admin/
    ├── AdminController.java        # /admin/**
    └── dto/
        └── AnalysisStatusDto.java
```

---

## 4. 데이터베이스 스키마

### games

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| name | VARCHAR(100) NOT NULL | 게임명 |
| url | VARCHAR(500) NOT NULL | 배경화면 페이지 URL |
| status | VARCHAR(20) | ACTIVE / UPDATING / FAILED |
| created_at | DATETIME | 등록 시각 |
| last_crawled_at | DATETIME | 마지막 크롤링 완료 시각 |

### wallpapers

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| game_id | BIGINT FK | games.id |
| file_name | VARCHAR(255) | 저장 파일명 |
| url | VARCHAR(500) | 서빙 URL (영구) |
| width | INT | 이미지 너비 (px) |
| height | INT | 이미지 높이 (px) |
| blur_hash | VARCHAR(100) | BlurHash 문자열 |
| tags | TEXT | JSON 배열 `["dark","landscape"]` |
| description | TEXT | AI 생성 설명 (Phase 4) |
| image_type | VARCHAR(20) | mobile / desktop / both |
| created_at | DATETIME | 수집 시각 |

### crawler_strategies

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| game_id | BIGINT FK | games.id |
| strategy_json | TEXT | 파싱 전략 JSON |
| version | INT | 버전 (재분석마다 증가) |
| analyzed_at | DATETIME | 분석 시각 |
| analysis_status | VARCHAR(20) | PENDING / ANALYZING / COMPLETED / FAILED |

### crawling_logs

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| game_id | BIGINT FK | games.id |
| started_at | DATETIME | 크롤링 시작 시각 |
| finished_at | DATETIME | 크롤링 완료 시각 |
| collected_count | INT | 수집된 이미지 수 |
| status | VARCHAR(20) | SUCCESS / FAILED |
| error_message | TEXT | 실패 시 오류 메시지 |

### user_likes (Phase 5)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| device_id | VARCHAR(100) | 클라이언트 기기 식별자 |
| wallpaper_id | BIGINT FK | wallpapers.id |
| created_at | DATETIME | 좋아요 시각 |

---

## 5. StorageService 인터페이스

```java
public interface StorageService {
    // 이미지 업로드 → 저장된 영구 URL 반환
    String upload(Long gameId, String fileName, byte[] data);

    // 파일명으로 서빙 URL 조회
    String getUrl(Long gameId, String fileName);

    // 파일 삭제
    void delete(Long gameId, String fileName);

    // 게임 폴더 내 파일 목록
    List<String> listFiles(Long gameId);
}
```

**LocalStorageService 저장 경로**:
- 파일 저장: `{STORAGE_ROOT}/images/{gameId}/{fileName}`
- 서빙 URL: `http://{host}:8080/storage/images/{gameId}/{fileName}`
- Spring `ResourceHandler` 로 `/storage/**` 정적 서빙 등록

---

## 6. 파싱 전략 JSON 스키마

```json
{
  "$schema": "v1",
  "requiresJavaScript": true,
  "preActions": [
    {
      "type": "click",
      "selector": ".popup-close",
      "optional": true,
      "waitMsAfter": 500
    }
  ],
  "paginationType": "button_click",
  "nextPageSelector": ".btn-next",
  "stopCondition": "selector_disabled:.btn-next",
  "imageSelector": ".wallpaper-list img",
  "imageAttribute": "src",
  "imageUrlPattern": "https://.*\\.(jpg|png|webp)",
  "imageType": "mobile",
  "waitMs": 2000,
  "maxPages": 50
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| requiresJavaScript | boolean | Selenium 필요 여부 (false → Jsoup 사용) |
| preActions | array | 페이지 사전 동작 목록 |
| paginationType | string | `button_click` / `scroll` / `url_pattern` / `none` |
| nextPageSelector | string | 다음 페이지 버튼 CSS 선택자 |
| stopCondition | string | 크롤링 중단 조건 (`selector_disabled:`, `selector_absent:`, `page_count:N`) |
| imageSelector | string | 이미지 요소 CSS 선택자 |
| imageAttribute | string | URL이 담긴 속성 (`src`, `data-src` 등) |
| imageUrlPattern | string | 유효 이미지 URL 정규식 필터 |
| imageType | string | `mobile` / `desktop` / `both` |
| waitMs | int | 페이지 로딩 대기 시간 (ms) |
| maxPages | int | 최대 페이지 수 (무한 루프 방지) |

---

## 7. Claude API 연동

### 파싱 전략 생성 프롬프트 구조

```
[System]
당신은 웹 크롤러 파싱 전략을 생성하는 전문가입니다.
주어진 HTML과 스크린샷을 분석하여 이미지 수집 전략을 JSON으로 반환하세요.
반환 형식은 반드시 다음 스키마를 따릅니다: {스키마 정의}

[User]
대상 URL: {url}
HTML (압축): {html_excerpt}
[스크린샷 이미지 첨부]

이 페이지에서 배경화면 이미지를 수집하기 위한 파싱 전략을 생성하세요.
```

### 태그 생성 프롬프트 구조

```
[System]
배경화면 이미지를 분석하여 검색에 유용한 태그를 생성합니다.
태그는 영문 소문자, 하이픈 사용, 최대 10개.

[User]
[이미지 첨부]

이 배경화면에 어울리는 태그를 JSON 배열로 반환하세요.
예: ["dark", "landscape", "blue-tone", "no-character"]
```

### API 호출 설정

| 항목 | 값 |
|------|----|
| 모델 | claude-3-5-sonnet-20241022 |
| 환경변수 | `ANTHROPIC_API_KEY` |
| 파싱 전략 max_tokens | 1024 |
| 태그 생성 max_tokens | 256 |
| 재시도 | Exponential backoff (최대 3회) |
| 타임아웃 | 60초 |

---

## 8. REST API 스펙

### 클라이언트 API

#### GET /api/games
```json
[
  {
    "id": 1,
    "name": "원신",
    "wallpaperCount": 142,
    "status": "ACTIVE",
    "lastCrawledAt": "2026-03-13T06:00:00Z"
  }
]
```

#### GET /api/wallpapers/{gameId}?page=0&size=12
```json
{
  "content": [
    {
      "id": 1,
      "url": "http://localhost:8080/storage/images/1/abc.jpg",
      "width": 1080,
      "height": 1920,
      "blurHash": "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
      "tags": ["landscape", "blue-tone"],
      "description": "파란 하늘 아래 펼쳐진 몬드 도시 전경",
      "imageType": "mobile"
    }
  ],
  "totalElements": 142,
  "totalPages": 12,
  "currentPage": 0,
  "size": 12
}
```

#### GET /api/wallpapers/search?tags=dark,landscape&page=0&size=12
- 태그 AND 검색 (기본값), `?mode=or` 파라미터로 OR 검색

#### GET /api/wallpapers/recommended
- 헤더: `X-Device-Id: {device_id}`
- 좋아요 이력 없으면 인기순(수집량 기준) 반환

#### POST /api/wallpapers/{id}/like
- 헤더: `X-Device-Id: {device_id}`
- 토글 방식 (좋아요 → 취소 → 좋아요)

---

## 9. 환경 변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `ANTHROPIC_API_KEY` | Claude API 키 | `sk-ant-...` |
| `STORAGE_ROOT` | 이미지 저장 루트 경로 | `/app/storage` |
| `SERVER_PORT` | 서버 포트 | `8080` |
| `SELENIUM_HUB_URL` | Selenium standalone URL | `http://selenium:4444` |
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `local` |

---

## 10. Docker Compose 구성

```yaml
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./storage:/app/storage        # 이미지 영속화
      - ./gamepaper.db:/app/gamepaper.db  # SQLite 영속화
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - SELENIUM_HUB_URL=http://selenium:4444
    depends_on:
      - selenium

  selenium:
    image: selenium/standalone-chrome:latest
    shm_size: "2g"
    ports:
      - "4444:4444"
```

**Dockerfile (멀티스테이지)**:
```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 11. 성능 / 안정성 고려사항

| 항목 | 전략 |
|------|------|
| SQLite 동시 쓰기 | WAL 모드 활성화 (`PRAGMA journal_mode=WAL`) |
| Selenium 메모리 | 크롤링 완료 후 세션 명시적 종료, Docker `shm_size: 2g` |
| 크롤링 속도 제한 | 요청 간 2~5초 딜레이, User-Agent 설정 |
| Claude API rate limit | Exponential backoff 재시도, 배치 처리 간격 조절 |
| 크롤링 타임아웃 | 게임당 최대 30분 (무한 루프 방지) |
| AI 분석 비동기 | `@Async` + `CompletableFuture`, 상태 polling API 제공 |
| 이미지 중복 방지 | 파일명 해시 기반 중복 체크 후 저장 |

---

## 12. 보안 고려사항

| 항목 | 방안 |
|------|------|
| API Key 노출 | 환경변수 관리, `.env` 파일은 `.gitignore` 등록 |
| Admin UI 접근 | Spring Security 기본 인증 또는 IP 제한 (Phase 1) |
| SQL Injection | JPA 파라미터 바인딩 사용, 네이티브 쿼리 최소화 |
| XSS | Thymeleaf 자동 이스케이핑 활용 |
| 파일 업로드 | 이미지 확장자/MIME 타입 검증, 경로 traversal 방지 |

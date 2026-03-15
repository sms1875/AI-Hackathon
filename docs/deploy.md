# GamePaper 배포 및 검증 가이드

서버 배포는 `server/deploy.md`에서, 전체 프로젝트 검증 항목은 이 문서에서 관리합니다.

---

## Sprint 6 배포 가이드

### 주요 변경 사항

- **에러 응답 표준화**: `GlobalExceptionHandler` + `ErrorCode` + `ErrorResponse` 추가 — 모든 API 에러가 `{ "error": { "code": "...", "message": "..." } }` 형태로 반환됩니다.
- **컨트롤러 분리**: `WallpaperLikeApiController` (좋아요), `WallpaperRecommendApiController` (추천) 신규 분리
- **WallpaperSearchService**: `ObjectMapper` static 상수화, `findAllTagged(Pageable)` 500건 배치 처리
- **Flutter 구조화 에러 처리**: `AppError` 클래스 + `AppErrorType` enum, 서버 에러 코드 매핑
- **Flutter 영속 캐시**: `LocalCache` (SharedPreferences TTL 기반) — 게임 목록 1시간 캐시, 배경화면 페이지별 캐시
- **Flutter 무한 스크롤**: `WallpaperScreen` PageView → GridView + ScrollController, 12개씩 추가 로드

---

## Sprint 5 배포 가이드

### 추가된 환경변수

```
# Claude API 키 — 태그 자동 생성에 필요 (미설정 시 태그 생성 건너뜀)
ANTHROPIC_API_KEY=sk-ant-...
```

### 주요 변경 사항

- **TaggingService**: 크롤링 파이프라인에 Claude Vision API 기반 자동 태그 생성 추가
- **BatchTaggingService**: 기존 수집 이미지 일괄 태깅 (`POST /admin/api/games/{id}/tagging`)
- **WallpaperSearchService**: 태그 AND/OR 검색 (`GET /api/wallpapers/search`)
- **TagApiController**: 태그 목록 API (`GET /api/tags`)
- **UserLike 엔티티**: 좋아요 토글 API (`POST /api/wallpapers/{id}/like`, device-id 헤더)
- **RecommendationService**: 좋아요 이력 기반 추천 (`GET /api/wallpapers/recommended`)
- **Flutter**: TagFilterChips, RecommendedSection, WallpaperCard 좋아요 버튼, shared_preferences 추가

---

## Sprint 4 배포 가이드

### 추가된 환경변수

```
# Claude API 키 (없으면 데모 전략으로 동작)
ANTHROPIC_API_KEY=sk-ant-...
```

`server/.env` 파일에 위 항목을 추가하세요. `ANTHROPIC_API_KEY` 미설정 시 AI 분석은 데모 전략을 반환합니다.

### 주요 변경 사항

- **DataInitializer**: 앱 시작 시 6개 게임 초기 데이터 자동 등록 (원신, 마비노기, 메이플스토리 모바일, NIKKE, 파이널판타지 XIV, 검은사막)
- **GenericCrawlerExecutor**: 기존 6개 게임별 크롤러 클래스 제거, 전략 JSON 기반 범용 크롤러로 대체
- **AI 분석 API**: `POST /admin/games/{id}/analyze` (비동기), `GET /admin/games/{id}/analyze/status` (polling)

---

## Sprint 3 배포 가이드

### 추가된 환경변수

```
# Claude API 키 (없으면 데모 모드로 동작)
ANTHROPIC_API_KEY=sk-ant-...

# Claude 모델 설정 (선택, 기본값: claude-3-5-sonnet-20241022)
CLAUDE_MODEL=claude-3-5-sonnet-20241022
```

`server/.env` 파일에 위 항목을 추가하세요. `ANTHROPIC_API_KEY` 미설정 시 `/admin/games/new`의 AI 분석 버튼은 데모 전략을 반환합니다.

### 관리자 UI 접속

서버 기동 후 `http://localhost:8080/admin` 으로 접속합니다.

---

## Sprint 2 배포 가이드

### 사전 준비

```bash
cd server/

# SQLite DB 파일 초기화 (없으면 Docker가 디렉토리로 마운트)
touch gamepaper.db

# 이미지 스토리지 디렉토리 생성
mkdir -p storage/images

# 환경변수 파일 복사
cp .env.example .env
```

### 서버 빌드 및 실행

```bash
# Sprint 2: 백엔드 + Selenium 컨테이너 포함
docker compose up --build
```

서버 기동 후 약 60~90초 내에 `Started GamepaperApplication` 로그가 출력됩니다.
Selenium 컨테이너가 헬스체크를 통과해야 백엔드가 기동됩니다.

### 환경변수 (.env)

```
SELENIUM_HUB_URL=http://selenium:4444
CRAWLER_SCHEDULE_DELAY_MS=21600000   # 6시간 (테스트: 60000 = 1분)
```

---

## 자동 검증 완료 항목

### Sprint 1 (기존)

- ✅ Gradle 빌드 성공 (`BUILD SUCCESSFUL`)
- ✅ 단위/통합 테스트 8개 전체 통과 (GameRepositoryTest, LocalStorageServiceTest, GameApiControllerTest)
- ✅ `GET /api/games` 빈 목록 반환 (MockMvc 검증)
- ✅ LocalStorageService URL 생성 로직 검증

### Sprint 2 (신규)

- ✅ ImageProcessorTest 5개 통과 (해상도 추출, BlurHash 생성, UUID 파일명, 잘못된 이미지 처리, 확장자 추출)
- ✅ CrawlingLog.status enum 타입 통일 (Sprint 1 I-3 이슈 해소)
- ✅ GitHub Actions CI 파이프라인 설정 완료 (.github/workflows/ci.yml)
- ✅ Selenium 서비스 docker-compose.yml에 추가 (shm_size 2g, 헬스체크)
- ✅ 게임 초기 데이터 SQL 작성 (6개 게임, INSERT OR IGNORE)
- ✅ Flutter 앱 Firebase Storage → 서버 REST API 전환 (별도 레포 커밋: 25aaf87)

### Sprint 3 (신규)

- ✅ Gradle clean test 전체 통과 (22개 테스트: AdminAnalyzeApiControllerTest 2, GameApiControllerTest 2, CrawlerStrategyParserTest 4, ImageProcessorTest 5, GameRepositoryTest 2, LocalStorageServiceTest 7)
- ✅ CrawlerStrategy 엔티티 + Repository 구현 (파싱 전략 버전 관리)
- ✅ ClaudeApiClient 구현 (Spring RestClient, API 키 미설정 시 IllegalStateException)
- ✅ CrawlerStrategyParser 구현 (JSON 코드 블록 추출, 필수 필드 검증)
- ✅ AdminAnalyzeApiController 구현 (POST /admin/api/analyze, 데모 모드 지원)
- ✅ LocalStorageService.listFiles() 구현 (Sprint 1 미구현 항목 완성)
- ✅ Thymeleaf 관리자 UI 4페이지 구현 (/admin, /admin/games, /admin/games/new, /admin/games/{id})

### Sprint 6 (신규)

- ✅ `./gradlew test --no-daemon --rerun-tasks` — BUILD SUCCESSFUL
- ✅ 전체 서버 테스트 50개 PASS (0 failures, 0 errors)
- ✅ WallpaperSearchServiceTest 8개 PASS (AND/OR 검색, null/빈 태그, JSON 파싱, 태그 빈도 분석)
- ✅ RecommendationServiceTest 4개 PASS (좋아요 이력, 태그 기반 추천, 중복 제외)
- ✅ WallpaperApiControllerTest 3개 PASS (페이지 조회, 404 에러, 페이지 파라미터)
- ✅ ClaudeApiClientTest 2개 PASS (API 키 미설정 예외 검증)
- ✅ ErrorResponseTest 2개 PASS (404/400 구조화 에러 응답)
- ✅ WallpaperSearchService ObjectMapper static 상수화 완료 (I-1 해소)
- ✅ WallpaperRepository.findAllTagged(Pageable) 추가 (I-2 부분 해소)
- ✅ WallpaperLikeApiController / WallpaperRecommendApiController 분리 완료 (I-3 해소)
- ✅ GlobalExceptionHandler + ErrorCode + ErrorResponse 구현
- ✅ flutter analyze — 신규 파일 에러 없음
- ✅ Flutter AppError 구조화 에러 처리 구현
- ✅ Flutter LocalCache (SharedPreferences TTL 기반) 구현
- ✅ Flutter WallpaperProvider 무한 스크롤 구현
- ✅ Flutter WallpaperScreen GridView + ScrollController 전환

### Sprint 5 (신규)

- ✅ Gradle compileJava + compileTestJava BUILD SUCCESSFUL (sprint-close 자동 실행)
- ✅ flutter analyze — 에러 없음
- ✅ TaggingService 구현 (Claude Vision API, 예외 시 빈목록 반환으로 파이프라인 보호)
- ✅ ClaudeApiClient.generateTagsFromImage() Vision API 메서드 추가
- ✅ BatchTaggingService 구현 (기존 이미지 일괄 태깅)
- ✅ GenericCrawlerExecutor — TaggingService 연동 (크롤링 후 태그 자동 생성)
- ✅ StorageService/LocalStorageService.download() 추가
- ✅ WallpaperRepository.findAllByTagsIsNull() / findAllTagged() 추가
- ✅ GET /api/wallpapers/search?tags=... 구현 (AND/OR 모드 지원)
- ✅ GET /api/tags 구현 (사용 빈도순 정렬)
- ✅ UserLike 엔티티 + UserLikeRepository 구현
- ✅ POST /api/wallpapers/{id}/like 좋아요 토글 구현
- ✅ RecommendationService 구현 (태그 빈도 분석 → OR 검색 → 좋아요 제외)
- ✅ GET /api/wallpapers/recommended 추천 API 구현
- ✅ Flutter Wallpaper 모델 tags/likeCount 필드 추가
- ✅ Flutter ApiConfig 4개 URL 추가
- ✅ Flutter GameRepository 4개 메서드 추가
- ✅ Flutter TagFilterChips / RecommendedSection / WallpaperCard 좋아요 버튼 구현
- ✅ Flutter DeviceId 유틸리티 (SharedPreferences 기반)

### Sprint 4 (신규)

- ✅ Gradle compileJava + compileTestJava BUILD SUCCESSFUL
- ✅ Sprint 3 코드 리뷰 이슈 3건 해소 (@Async 스레드풀, RestClient.Builder 재사용, GameStatus.INACTIVE)
- ✅ AnalysisStatus 열거형 생성 (PENDING, ANALYZING, COMPLETED, FAILED)
- ✅ AnalysisService 비동기 AI 분석 파이프라인 구현 (@Async("asyncExecutor"))
- ✅ POST /admin/games/{id}/analyze 트리거 API 구현 (202 Accepted)
- ✅ GET /admin/games/{id}/analyze/status 상태 polling API 구현
- ✅ GenericCrawlerExecutor 구현 (4가지 페이지네이션: none, button_click, scroll, url_pattern)
- ✅ DataInitializer — 앱 시작 시 6개 게임 초기 데이터 자동 등록 (멱등성 보장)
- ✅ CrawlerScheduler — 전략 있는 게임 GenericCrawlerExecutor 우선 실행, INACTIVE 제외
- ✅ 기존 크롤러 6개 제거 (GenshinCrawler, MabinogiCrawler, MapleStoryCrawler, NikkeCrawler, FinalFantasyXIVCrawler, BlackDesertCrawler)
- ✅ 프론트엔드 AI 분석 polling UI (game-new.html, game-detail.html, game-list.html)

---

## 수동 검증 필요 항목

### Sprint 6 수동 검증 항목

#### Docker 재빌드

- ⬜ `docker compose up --build` — Sprint 6 코드 반영 후 서버 정상 기동
  ```bash
  cd server/
  docker compose up --build
  # 확인: "Started GamepaperApplication" 로그 출력
  ```

#### 에러 응답 표준화 확인

- ⬜ 존재하지 않는 게임 조회 → 구조화된 404 응답 확인
  ```bash
  curl -s http://localhost:8080/api/games/99999 | python -m json.tool
  # 예상: {"error": {"code": "GAME_NOT_FOUND", "message": "..."}}
  ```

- ⬜ device-id 없이 좋아요 요청 → 400 구조화 에러 응답 확인
  ```bash
  curl -s -X POST http://localhost:8080/api/wallpapers/1/like | python -m json.tool
  # 예상: {"error": {"code": "MISSING_DEVICE_ID", "message": "..."}}
  ```

- ⬜ 배경화면 페이지 조회
  ```bash
  curl -s "http://localhost:8080/api/wallpapers/1?page=0&size=12" | python -m json.tool
  # 예상: content 배열 + totalPages, currentPage 포함
  ```

#### Flutter 앱 UI 시각적 확인

- ⬜ 오프라인 상태에서 앱 실행 → 오프라인 배너 표시 확인
- ⬜ 앱 재시작 시 게임 목록 즉시 표시 (SharedPreferences 캐시) 확인
- ⬜ 배경화면 목록 하단 스크롤 → 무한 스크롤 추가 로드 동작 확인
- ⬜ 서버 에러 시 AppError 기반 사용자 메시지 표시 확인

---

### Docker 환경 검증

- ⬜ `docker compose up --build` — backend + selenium 컨테이너 정상 기동 확인
  ```bash
  # 확인 방법: 로그에서 Started GamepaperApplication 메시지 확인
  docker compose logs backend | grep "Started"
  ```

- ⬜ API 응답 확인 — 6개 게임 목록 반환
  ```bash
  curl http://localhost:8080/api/games
  # 예상: 6개 게임 목록 (id 1~6, 원신/마비노기/메이플/NIKKE/FFXIV/검은사막)
  ```

- ⬜ Selenium 헬스체크 확인
  ```bash
  curl http://localhost:4444/wd/hub/status
  # 예상: {"value":{"ready":true,...}}
  ```

### 크롤링 검증

- ⬜ 크롤링 테스트 실행 (단축 주기)
  ```bash
  # .env에서 CRAWLER_SCHEDULE_DELAY_MS=60000 으로 변경 후 재시작
  docker compose restart backend
  # 1분 후 크롤링 자동 실행 → 로그 확인
  docker compose logs -f backend | grep "크롤링"
  ```

- ⬜ 수집된 배경화면 API 조회
  ```bash
  # 크롤링 완료 후 확인
  curl "http://localhost:8080/api/wallpapers/1?page=0&size=12"
  # 예상: content 배열에 항목, blurHash 비어 있지 않음
  ```

- ⬜ 이미지 URL 접근 확인
  ```bash
  # API 응답의 url 필드로 접속
  curl -I http://localhost:8080/storage/images/1/{파일명}
  # 예상: HTTP 200
  ```

### Flutter 앱 검증 (수동)

- ⬜ 서버 IP 설정 후 앱 실행
  - Android 에뮬레이터: `10.0.2.2:8080`
  - 실기기: 로컬 네트워크 IP (예: `192.168.x.x:8080`)

- ⬜ 게임 목록 화면 표시 확인

- ⬜ 배경화면 그리드 표시 확인 (BlurHash placeholder 포함)

- ⬜ 배경화면 선택 후 기기 적용 확인

### GitHub Actions 확인

- ⬜ PR 생성 후 CI 통과 확인
  - URL: https://github.com/sms1875/AI-Hackathon/actions

---

## Sprint 3 수동 검증 항목

### Docker 환경 재빌드

- ⬜ `docker compose up --build` — Sprint 3 코드 반영 후 서버 정상 기동
  ```bash
  cd server/
  docker compose up --build
  # 확인: "Started GamepaperApplication" 로그 출력
  ```

### 관리자 UI 브라우저 검증

- ⬜ `http://localhost:8080/admin` 대시보드 접속 — 요약 카드 4개, 크롤링 로그 타임라인 표시 확인

- ⬜ `http://localhost:8080/admin/games` 게임 목록 — 게임 테이블, 상태 뱃지, 액션 버튼 표시 확인

- ⬜ `http://localhost:8080/admin/games/new` 게임 등록 폼 — 게임명/URL 입력, AI 분석 버튼 동작 확인

- ⬜ `http://localhost:8080/admin/games/{id}` 게임 상세 — 3탭(배경화면/파싱전략/크롤링로그) 전환 확인

### AI 분석 API 검증

- ⬜ 데모 모드 AI 분석 테스트 (ANTHROPIC_API_KEY 미설정)
  ```bash
  curl -s -X POST http://localhost:8080/admin/api/analyze \
    -H "Content-Type: application/json" \
    -d '{"url":"https://example.com"}' | python -m json.tool
  # 예상: {"strategy": {...}, "warning": "ANTHROPIC_API_KEY가 설정되지 않아..."}
  ```

- ⬜ 수동 크롤링 트리거 API
  ```bash
  curl -s -X POST http://localhost:8080/admin/api/games/1/crawl
  # 예상: {"message": "크롤링을 시작했습니다."}
  ```

---

## Sprint 5 수동 검증 항목

### Docker 환경 재빌드

- ⬜ `docker compose up --build` — Sprint 5 코드 반영 후 서버 정상 기동
  ```bash
  cd server/
  docker compose up --build
  # 확인: "Started GamepaperApplication" 로그 출력
  ```

### 태그 생성 실제 테스트

- ⬜ `ANTHROPIC_API_KEY` 설정 확인 (`server/.env`)

- ⬜ 크롤링 실행 후 배경화면 tags 필드에 태그 자동 생성 확인
  ```bash
  curl "http://localhost:8080/api/wallpapers/1?page=0&size=5"
  # 예상: 각 배경화면의 tags 필드에 ["dark","landscape"] 형태 데이터
  ```

- ⬜ 기존 이미지 일괄 태깅 실행
  ```bash
  curl -s -X POST http://localhost:8080/admin/api/games/1/tagging
  # 예상: {"message": "배치 태깅을 시작했습니다."} 또는 결과 카운트
  ```

### 검색 API 확인

- ⬜ 태그 AND 검색 테스트
  ```bash
  curl "http://localhost:8080/api/wallpapers/search?tags=dark&mode=and"
  # 예상: dark 태그를 포함한 배경화면 목록 (최대 50개)
  ```

- ⬜ 태그 OR 검색 테스트
  ```bash
  curl "http://localhost:8080/api/wallpapers/search?tags=dark,landscape&mode=or"
  # 예상: dark 또는 landscape 태그가 있는 배경화면 목록
  ```

- ⬜ 태그 목록 API 확인
  ```bash
  curl "http://localhost:8080/api/tags"
  # 예상: [{"tag":"dark","count":15}, ...] 빈도순 정렬
  ```

### 좋아요 및 추천 API 확인

- ⬜ 좋아요 토글 테스트
  ```bash
  curl -s -X POST -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/1/like
  # 예상: {"liked": true, "likeCount": 1}
  # 재호출 시: {"liked": false, "likeCount": 0}
  ```

- ⬜ 추천 API 확인 (좋아요 이력 없을 때)
  ```bash
  curl -s -H "device-id: new-device-999" http://localhost:8080/api/wallpapers/recommended
  # 예상: [] (빈 배열)
  ```

- ⬜ 추천 API 확인 (좋아요 이력 있을 때)
  ```bash
  # 좋아요 몇 개 등록 후
  curl -s -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/recommended
  # 예상: 좋아요한 배경화면 태그와 유사한 배경화면 목록
  ```

### Flutter 앱 UI 확인 (수동)

- ⬜ 배경화면 그리드 상단에 태그 필터 칩 표시 확인
- ⬜ 태그 칩 선택 시 필터링된 배경화면 표시 확인
- ⬜ 홈 화면에 "추천 배경화면" 섹션 표시 (좋아요 이력 있는 경우)
- ⬜ 배경화면 카드 하트 버튼 클릭 → 좋아요 상태 토글 확인

---

## Sprint 4 수동 검증 항목

### Docker 환경 재빌드

- ⬜ `docker compose up --build` — Sprint 4 코드 반영 후 컨테이너 정상 기동 확인
  ```bash
  cd server/
  docker compose up --build
  # 확인: "Started GamepaperApplication" 로그 출력
  ```

- ⬜ DataInitializer 동작 확인 — 기동 로그에서 6개 게임 등록 메시지 확인
  ```bash
  docker compose logs backend | grep "초기 데이터"
  ```

### 관리자 UI 브라우저 검증

- ⬜ `http://localhost:8080/admin/games` — 게임 목록 6개 표시 + AI 분석 상태 뱃지 확인

- ⬜ `http://localhost:8080/admin/games/new` — "AI 분석 시작" 버튼 클릭 → polling 진행 상태 확인

- ⬜ `http://localhost:8080/admin/games/{id}` — "재분석" 버튼 클릭 → 새 버전 전략 생성 확인

- ⬜ 크롤링 트리거 → 로그 탭에서 수집 결과 확인

### AI 분석 API 검증

- ⬜ `POST /admin/games/{id}/analyze` — 202 Accepted 반환 확인
  ```bash
  curl -s -X POST http://localhost:8080/admin/games/1/analyze | python -m json.tool
  # 예상: {"status": "ANALYZING", "message": "AI 분석을 시작했습니다..."}
  ```

- ⬜ `GET /admin/games/{id}/analyze/status` — 상태 JSON 반환 확인
  ```bash
  curl -s http://localhost:8080/admin/games/1/analyze/status | python -m json.tool
  ```

- ⬜ `POST /admin/api/games/{id}/crawl` — GenericCrawlerExecutor 실행 확인
  ```bash
  curl -s -X POST http://localhost:8080/admin/api/games/1/crawl
  ```

### AI 분석 실제 테스트

- ⬜ `ANTHROPIC_API_KEY` 환경변수 설정 확인 (`server/.env`)

- ⬜ 실제 게임 URL 등록 → AI 분석 60초 이내 완료 확인

- ⬜ 생성된 전략 JSON 유효성 확인 (imageSelector, paginationType 포함)

---

## 주의 사항

- `docker compose up --build`는 새 코드 반영을 위해 사용자가 직접 실행해야 합니다 (재빌드 타이밍을 사용자가 결정).
- `alembic upgrade head` 또는 DB 스키마 변경은 해당되지 않음 (JPA auto-ddl 사용 중).
- Selenium 컨테이너는 메모리를 많이 사용합니다 (shm_size 2g). 메모리 부족 시 `SE_NODE_MAX_SESSIONS=1`은 유지합니다.

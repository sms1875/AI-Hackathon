# GamePaper AI — 배포 및 테스트 가이드

Sprint 1~6 전체 구현 완료 기준의 최종 배포·검증 가이드입니다.

---

## Prerequisites

| 항목 | 버전 | 용도 |
|------|------|------|
| Java | 21 | 로컬 Gradle 빌드 (테스트 실행) |
| Docker Desktop | 최신 | 서버 실행 (backend + selenium) |
| Flutter SDK | 3.x+ | 모바일 앱 빌드 및 실행 |
| Android SDK | API 21+ | Android 앱 실행 (에뮬레이터/실기기) |
| `ANTHROPIC_API_KEY` | — | AI 분석 및 이미지 태그 자동 생성 (없으면 데모 모드) |

---

## 1. 서버 실행 (Docker Compose)

### 1-1. 최초 실행 전 사전 준비

Docker는 마운트 대상 파일이 없으면 디렉토리로 마운트하기 때문에 SQLite가 정상 동작하지 않습니다.

```bash
cd server/

# SQLite DB 파일 초기화
touch gamepaper.db
# Windows CMD: type nul > gamepaper.db

# 이미지 스토리지 디렉토리 생성
mkdir -p storage/images
# Windows CMD: mkdir storage\images

# 환경변수 파일 복사 후 편집
cp .env.example .env
```

### 1-2. .env 파일 설정

```
# Claude API 키 (미설정 시 AI 분석은 데모 전략 반환, 태그 생성 건너뜀)
ANTHROPIC_API_KEY=sk-ant-api03-...

# 크롤링 주기 (ms). 테스트 시 60000(1분)으로 변경 가능
CRAWLER_SCHEDULE_DELAY_MS=21600000
```

### 1-3. 서버 빌드 및 실행

```bash
docker compose up --build
```

- 최초 빌드: 3~5분 소요 (Gradle 의존성 다운로드 + Docker 이미지 빌드)
- 이후 재시작: `docker compose up` (빌드 없이 시작)
- Selenium 컨테이너 헬스체크 통과 후 backend 기동 시작
- 기동 완료: `Started GamepaperApplication` 로그 출력 (약 60~90초)

### 1-4. 서버 재시작 / 중지

```bash
# 재시작 (코드 변경 반영)
docker compose up --build

# 중지
docker compose down

# 로그 확인
docker compose logs -f backend
docker compose logs -f selenium
```

---

## 2. Flutter 앱 실행

### 2-1. 의존성 설치

```bash
cd /path/to/GamePaper/client
flutter pub get
```

### 2-2. 서버 IP 설정

`lib/config/api_config.dart` 에서 baseUrl 확인 및 수정:

```dart
// Android 에뮬레이터 (10.0.2.2 = 호스트 PC localhost)
static const String baseUrl = 'http://10.0.2.2:8080';

// 실기기 (호스트 PC의 로컬 네트워크 IP)
// static const String baseUrl = 'http://192.168.x.x:8080';
```

### 2-3. 앱 실행

```bash
# 에뮬레이터 목록 확인
flutter emulators

# 에뮬레이터 실행
flutter emulators --launch <emulator_id>

# 앱 실행
flutter run
```

---

## 3. 기능별 테스트 방법

### 3-1. API 기본 동작 테스트

```bash
# 게임 목록 조회 (6개 게임 반환 확인)
curl http://localhost:8080/api/games
# 예상: [{"id":1,"name":"원신",...}, ...] 6개 항목

# 서버 헬스체크
curl -f http://localhost:8080/api/games && echo "OK"

# Selenium 헬스체크
curl http://localhost:4444/wd/hub/status
# 예상: {"value":{"ready":true,...}}
```

### 3-2. 관리자 UI 테스트 순서

브라우저에서 다음 순서로 확인:

1. **대시보드**: `http://localhost:8080/admin`
   - 전체 게임 수, 총 배경화면 수, 마지막 크롤링 시각 카드 확인
   - 최근 크롤링 로그 타임라인 확인

2. **게임 목록**: `http://localhost:8080/admin/games`
   - 6개 게임 표시, 상태 배지 (ACTIVE / FAILED / INACTIVE) 확인
   - AI 분석 상태 컬럼 확인

3. **게임 등록 폼**: `http://localhost:8080/admin/games/new`
   - 게임명 + URL 입력 후 "AI 분석 시작" 버튼 클릭
   - polling 진행 상태 표시 확인 ("페이지 접속 중..." → "전략 생성 완료")

4. **게임 상세**: `http://localhost:8080/admin/games/1`
   - 배경화면 / 파싱전략 / 크롤링로그 탭 전환 확인

### 3-3. AI 분석 테스트

```bash
# 데모 모드 테스트 (ANTHROPIC_API_KEY 미설정 시)
curl -s -X POST http://localhost:8080/admin/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}' | python -m json.tool
# 예상: {"strategy": {...}, "warning": "ANTHROPIC_API_KEY가 설정되지 않아..."}

# AI 분석 트리거 (게임 ID 1)
curl -s -X POST http://localhost:8080/admin/games/1/analyze | python -m json.tool
# 예상: {"status": "ANALYZING", "message": "AI 분석을 시작했습니다..."}

# AI 분석 상태 폴링
curl -s http://localhost:8080/admin/games/1/analyze/status | python -m json.tool
# 예상: {"status": "COMPLETED", "strategy": {...}} 또는 "ANALYZING"
```

### 3-4. 크롤링 테스트

```bash
# 수동 크롤링 실행 (게임 ID 1)
curl -s -X POST http://localhost:8080/admin/api/games/1/crawl
# 예상: {"message": "크롤링을 시작했습니다."}

# 크롤링 로그 실시간 확인
docker compose logs -f backend | grep "크롤링"

# 자동 크롤링 테스트 (.env에서 변경)
# CRAWLER_SCHEDULE_DELAY_MS=60000 으로 설정 후 재시작
docker compose restart backend
# 1분 후 자동 크롤링 실행 확인

# 크롤링 완료 후 배경화면 조회
curl "http://localhost:8080/api/wallpapers/1?page=0&size=12" | python -m json.tool
# 예상: content 배열에 항목, blurHash 비어 있지 않음

# 이미지 직접 접근
curl -I http://localhost:8080/storage/images/1/<파일명.jpg>
# 예상: HTTP/1.1 200
```

### 3-5. 태그 / 검색 / 추천 API 테스트

```bash
# 태그 목록 조회 (빈도순)
curl "http://localhost:8080/api/tags"
# 예상: [{"tag":"dark","count":15}, ...] 빈도순

# 태그 AND 검색
curl "http://localhost:8080/api/wallpapers/search?tags=dark&mode=and"
# 예상: dark 태그를 포함한 배경화면 목록

# 태그 OR 검색
curl "http://localhost:8080/api/wallpapers/search?tags=dark,landscape&mode=or"
# 예상: dark 또는 landscape 태그가 있는 배경화면 목록

# 기존 이미지 일괄 태깅 (ANTHROPIC_API_KEY 필요)
curl -s -X POST http://localhost:8080/admin/api/games/1/tagging
# 예상: {"message": "배치 태깅을 시작했습니다."}

# 크롤링 후 배경화면 tags 필드 확인
curl "http://localhost:8080/api/wallpapers/1?page=0&size=5" | python -m json.tool
# 예상: tags 필드에 ["dark","landscape"] 형태 데이터

# 좋아요 토글
curl -s -X POST -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/1/like
# 예상: {"liked": true, "likeCount": 1}
# 재호출 시: {"liked": false, "likeCount": 0}

# 추천 API (좋아요 이력 없을 때)
curl -s -H "device-id: new-device-999" http://localhost:8080/api/wallpapers/recommended
# 예상: [] (빈 배열)

# 추천 API (좋아요 몇 개 등록 후)
curl -s -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/recommended
# 예상: 좋아요한 배경화면의 태그와 유사한 배경화면 목록
```

### 3-6. 에러 처리 테스트

```bash
# 존재하지 않는 게임 조회 → 구조화된 404 응답
curl -s http://localhost:8080/api/games/99999 | python -m json.tool
# 예상: {"error": {"code": "GAME_NOT_FOUND", "message": "..."}}

# device-id 없이 좋아요 요청 → 400 에러
curl -s -X POST http://localhost:8080/api/wallpapers/1/like | python -m json.tool
# 예상: {"error": {"code": "MISSING_DEVICE_ID", "message": "..."}}

# 배경화면 페이지 조회
curl -s "http://localhost:8080/api/wallpapers/1?page=0&size=12" | python -m json.tool
# 예상: content 배열 + totalPages, currentPage 포함
```

---

## 4. Flutter 앱 수동 검증 항목

서버가 실행 중인 상태에서 Flutter 앱을 실행하여 아래 항목을 순서대로 확인합니다.

- ⬜ 앱 실행 시 게임 목록 화면 표시 확인 (6개 게임)
- ⬜ 게임 선택 후 배경화면 그리드 표시 확인 (BlurHash placeholder 포함)
- ⬜ 배경화면 그리드 하단 스크롤 → 무한 스크롤 추가 로드 동작 확인
- ⬜ 배경화면 그리드 상단에 태그 필터 칩 표시 확인 (태그가 있는 경우)
- ⬜ 태그 칩 선택 시 필터링된 배경화면 표시 확인
- ⬜ 배경화면 카드 하트 버튼 클릭 → 좋아요 상태 토글 확인
- ⬜ 홈 화면에 "추천 배경화면" 섹션 표시 (좋아요 이력 있는 경우)
- ⬜ 배경화면 선택 후 홈화면 / 잠금화면 적용 확인
- ⬜ 앱 재시작 시 게임 목록 즉시 표시 (SharedPreferences 캐시) 확인
- ⬜ 오프라인 상태에서 앱 실행 → 오프라인 배너 표시 확인
- ⬜ 서버 에러 시 사용자 친화적 메시지 표시 확인

---

## 5. 서버 테스트 실행

```bash
cd server/

# 전체 테스트 (50개)
./gradlew test

# Windows 환경
gradlew.bat test

# 빌드만 확인
./gradlew compileJava compileTestJava
```

예상 결과: `BUILD SUCCESSFUL` — 50개 테스트 전체 PASS

---

## 6. 자동 검증 완료 항목

Sprint별 자동 검증이 완료된 항목입니다.

### Sprint 1

- ✅ Gradle 빌드 성공 (`BUILD SUCCESSFUL`)
- ✅ 단위/통합 테스트 8개 전체 통과 (GameRepositoryTest, LocalStorageServiceTest, GameApiControllerTest)
- ✅ `GET /api/games` 빈 목록 반환 (MockMvc 검증)
- ✅ LocalStorageService URL 생성 로직 검증

### Sprint 2

- ✅ ImageProcessorTest 5개 통과 (해상도 추출, BlurHash 생성, UUID 파일명, 잘못된 이미지 처리)
- ✅ CrawlingLog.status enum 타입 통일
- ✅ GitHub Actions CI 파이프라인 설정 완료 (`.github/workflows/ci.yml`)
- ✅ Selenium 서비스 docker-compose.yml에 추가

### Sprint 3

- ✅ Gradle clean test 전체 통과 (22개 테스트)
- ✅ CrawlerStrategy 엔티티 + Repository 구현
- ✅ ClaudeApiClient 구현 (API 키 미설정 시 IllealStateException)
- ✅ AdminAnalyzeApiController 구현 (POST /admin/api/analyze, 데모 모드 지원)
- ✅ Thymeleaf 관리자 UI 4페이지 구현

### Sprint 4

- ✅ Gradle compileJava + compileTestJava BUILD SUCCESSFUL
- ✅ AnalysisService 비동기 AI 분석 파이프라인 구현
- ✅ GenericCrawlerExecutor 구현 (4가지 페이지네이션 타입)
- ✅ DataInitializer — 6개 게임 초기 데이터 자동 등록
- ✅ 기존 크롤러 6개 제거 완료

### Sprint 5

- ✅ Gradle compileJava + compileTestJava BUILD SUCCESSFUL
- ✅ flutter analyze — 에러 없음
- ✅ TaggingService 구현 (Claude Vision API)
- ✅ WallpaperSearchService AND/OR 검색 구현
- ✅ UserLike 엔티티 + 좋아요 토글 API
- ✅ RecommendationService 구현
- ✅ Flutter TagFilterChips, RecommendedSection, WallpaperCard 좋아요 버튼

### Sprint 6

- ✅ `./gradlew test` — BUILD SUCCESSFUL (50개 테스트 PASS)
- ✅ WallpaperSearchService ObjectMapper static 상수화 (I-1 해소)
- ✅ WallpaperRepository.findAllTagged(Pageable) 추가 (I-2 해소)
- ✅ WallpaperLikeApiController / WallpaperRecommendApiController 분리 (I-3 해소)
- ✅ GlobalExceptionHandler + ErrorCode + ErrorResponse 구현
- ✅ flutter analyze — 신규 파일 에러 없음
- ✅ Flutter AppError 구조화 에러 처리 구현
- ✅ Flutter LocalCache (SharedPreferences TTL) 구현
- ✅ Flutter WallpaperProvider 무한 스크롤 구현

---

## 7. 트러블슈팅 가이드

### 서버가 기동되지 않을 때

```bash
# 로그 확인
docker compose logs backend

# Selenium 헬스체크 상태 확인
docker compose logs selenium
curl http://localhost:4444/wd/hub/status

# 컨테이너 재시작
docker compose down
docker compose up --build
```

### SQLite 에러 (`unable to open database file`)

`gamepaper.db` 파일이 존재하지 않아 Docker가 디렉토리로 마운트한 경우:

```bash
docker compose down
# 잘못 생성된 디렉토리 제거
rm -rf server/gamepaper.db
# 파일로 재생성
touch server/gamepaper.db   # Windows: type nul > server/gamepaper.db
docker compose up --build
```

### 이미지가 표시되지 않을 때 (Flutter 앱)

- Android 에뮬레이터: `api_config.dart`에서 `10.0.2.2:8080` 확인
- 실기기: 서버 PC와 동일한 네트워크 연결 여부 확인, IP 주소 확인
- 크롤링 완료 여부 확인: `curl http://localhost:8080/api/wallpapers/1?page=0&size=5`

### Gradle 빌드 실패 (Windows 한글 경로)

`build.gradle`에 Windows 빌드 경로 우회 설정이 포함되어 있습니다. `C:/temp/gamepaper-build` 경로가 쓰기 가능한지 확인하세요.

```bash
mkdir C:\temp
./gradlew test
```

### `ANTHROPIC_API_KEY` 관련 에러

AI 분석 및 태그 생성 기능은 API 키 없이 데모 모드로 동작합니다. 데모 전략이 반환되며 태그 생성은 건너뜁니다. 실제 분석이 필요한 경우 `server/.env`에 키를 추가하세요.

### Selenium 메모리 부족

Selenium 컨테이너는 `shm_size: 2g`를 사용합니다. Docker Desktop 메모리 설정을 4GB 이상으로 설정하세요.

---

## 주의 사항

- `docker compose up --build`는 새 코드 반영을 위해 사용자가 직접 실행해야 합니다 (재빌드 타이밍은 사용자가 결정).
- JPA `hibernate.ddl-auto=update`를 사용하므로 별도 마이그레이션 명령이 필요 없습니다.
- Selenium 컨테이너가 헬스체크를 통과해야 backend가 기동됩니다. 느린 환경에서는 `start_period` 조정이 필요할 수 있습니다.
- GitHub Actions CI: https://github.com/sms1875/AI-Hackathon/actions

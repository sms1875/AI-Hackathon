# GamePaper 배포 및 검증 가이드

서버 배포는 `server/deploy.md`에서, 전체 프로젝트 검증 항목은 이 문서에서 관리합니다.

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

---

## 수동 검증 필요 항목

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

## 주의 사항

- `docker compose up --build`는 새 코드 반영을 위해 사용자가 직접 실행해야 합니다 (재빌드 타이밍을 사용자가 결정).
- `alembic upgrade head` 또는 DB 스키마 변경은 해당되지 않음 (JPA auto-ddl 사용 중).
- Selenium 컨테이너는 메모리를 많이 사용합니다 (shm_size 2g). 메모리 부족 시 `SE_NODE_MAX_SESSIONS=1`은 유지합니다.

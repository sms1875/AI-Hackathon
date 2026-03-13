# 프로젝트 로드맵 - GamePaper

## 개요
- **목표**: 게임 공식 배경화면을 AI 기반으로 자동 수집하여 모바일/데스크탑에 적용하는 앱 구축
- **전체 스프린트**: 8 스프린트
- **현재 진행 단계**: Phase 1 시작 전 (계획 수립 완료)

## 진행 상태 범례
- ✅ 완료
- 🔄 진행 중
- 📋 예정
- ⏸️ 보류

---

## 프로젝트 현황 대시보드

| 항목 | 상태 |
|------|------|
| 전체 진행률 | 0% |
| 현재 Phase | Phase 1 준비 중 |
| 다음 마일스톤 | Phase 1 완료 - 인프라 기초 + 기존 크롤러 동작 |
| 총 Phase | 4 |

---

## 기술 아키텍처 결정 사항

| 결정 | 선택 | 이유 |
|------|------|------|
| 백엔드 프레임워크 | Spring Boot 3.x (Java 21) | 기존 코드베이스 유지, 안정성 |
| Phase 1 DB | SQLite | 로컬 실행 단순화, 클라우드 전환 시 Repository 인터페이스만 교체 |
| Phase 1 스토리지 | 로컬 파일시스템 | signed URL 만료 문제 해결, 영구 URL 제공 |
| AI 엔진 | Claude API (Anthropic) | 범용 크롤러 파싱 전략 생성, 태그/추천/설명 생성 |
| 모바일 앱 | Flutter (Dart), Provider | 기존 앱 유지 |
| 크롤링 | Selenium + Jsoup | 기존 방식 유지, AI 크롤러에서 래핑하여 활용 |
| 관리자 UI | Thymeleaf (서버 사이드 렌더링) | 별도 프론트엔드 빌드 불필요, Spring Boot 내장 |
| 컨테이너화 | Docker Compose | 로컬/클라우드 환경 통일 |
| 환경 전환 | Spring Profile (local/prod) | properties 파일 교체만으로 구현체 자동 선택 |

---

## 의존성 맵

```
Phase 1: 인프라 기초
  ├── StorageService 추상화 ─────────────────────┐
  ├── DB Repository 추상화 ──────────────────────┤
  ├── Docker Compose 환경 ───────────────────────┤
  └── 기존 크롤러 마이그레이션 ──────────────────┤
                                                  ↓
Phase 2: AI 크롤러 + 관리자 페이지                │
  ├── 관리자 UI (Thymeleaf) ← Phase 1 인프라 필요│
  ├── AI 범용 크롤러 ← StorageService + DB 필요  │
  ├── 배경화면 자동 태그 ← 크롤러 파이프라인 필요│
  └── AI 추천 ← 태그 데이터 필요                 │
                                                  ↓
Phase 3: 리팩토링                                 │
  ├── 클라이언트 캐시 ← 서버 API 안정화 후       │
  ├── 에러 처리 구조화 ← 독립적                  │
  ├── 페이지네이션 개선 ← 서버 API 필요          │
  └── 테스트 코드 ← 전체 구조 안정화 후          │
                                                  ↓
Phase 4: 멀티플랫폼 확장                          │
  ├── 데스크탑 지원 ← 클라이언트 안정화 후       │
  ├── 해상도별 추천 ← DB 메타데이터 필요         │
  ├── PC 배경화면 수집 ← AI 크롤러 필요          │
  └── 좋아요/즐겨찾기 ← DB + 클라이언트 안정화   │
```

---

## Phase 1: 인프라 기초 (Sprint 1-2)

### 목표
DB와 스토리지 추상화 레이어를 구축하고, Docker 환경을 구성하여 기존 6개 게임 크롤러가 새 인프라 위에서 동작하도록 마이그레이션한다.

### MoSCoW 분류
- **Must Have**: StorageService 추상화, DB Repository 추상화, Docker Compose
- **Should Have**: 기존 6개 크롤러 마이그레이션, 클라이언트 API 엔드포인트 연결
- **Could Have**: CI/CD 기본 파이프라인

---

### Sprint 1: 백엔드 인프라 추상화

#### 작업 목록

- 📋 **S1-1. Spring Boot 프로젝트 초기화** [복잡도: S]
  - Spring Boot 3.x + Java 21 프로젝트 생성 (Gradle)
  - 기본 의존성 추가: Spring Web, Spring Data JPA, SQLite JDBC, Thymeleaf
  - `application.yml` 프로파일 구성 (`local` / `prod`)
  - 프로젝트 패키지 구조 설계:
    ```
    com.gamepaper
    ├── config/        # 설정 클래스
    ├── domain/        # 엔티티, Repository 인터페이스
    ├── storage/       # StorageService 추상화
    ├── crawler/       # 크롤러 관련
    ├── api/           # REST API 컨트롤러
    └── admin/         # 관리자 페이지 컨트롤러
    ```

- 📋 **S1-2. StorageService 추상화 구현** [복잡도: M]
  - `StorageService` 인터페이스 정의: `upload(gameId, fileName, bytes)`, `getUrl(gameId, fileName)`, `delete(gameId, fileName)`
  - `LocalStorageService` 구현: 로컬 파일시스템 (`/storage/images/{gameId}/`) 저장
  - Spring `ResourceHandler`로 `/storage/**` 경로 정적 파일 서빙 (영구 URL)
  - `@Profile("local")` 어노테이션으로 프로파일 연동
  - 단위 테스트: 파일 업로드/조회/삭제 확인
  - `listFiles(gameId)` 는 Sprint 3 Admin UI 배경화면 탭 구현 시 추가

- 📋 **S1-3. DB Repository 추상화 구현** [복잡도: M]
  - JPA 엔티티 정의:
    - `Game`: id, name, url, status(ACTIVE/UPDATING/FAILED), createdAt, lastCrawledAt
    - `Wallpaper`: id, gameId, fileName, url, width, height, blurHash, tags(JSON), createdAt
    - `CrawlingLog`: id, gameId, startedAt, finishedAt, collectedCount, status, errorMessage
  - `GameRepository`, `WallpaperRepository`, `CrawlingLogRepository` (Spring Data JPA)
  - SQLite 연결 설정 (`application-local.yml`): `jdbc:sqlite:gamepaper.db`
  - Flyway 또는 JPA auto-ddl로 스키마 자동 생성
  - 통합 테스트: CRUD 동작 확인

- 📋 **S1-4. 클라이언트용 REST API 구현** [복잡도: M]
  - `GET /api/games` - 게임 목록 (이름, 배경화면 수, 상태)
  - `GET /api/wallpapers/{gameId}` - 게임별 배경화면 목록 (페이징 지원, 12개/페이지)
  - JSON 응답 DTO 설계 (GameDto, WallpaperDto, PagedResponse)
  - API 동작 테스트 (MockMvc 또는 수동 curl)

- 📋 **S1-5. Docker Compose 로컬 환경 구성** [복잡도: S]
  - `Dockerfile`: 멀티스테이지 빌드 (Gradle 빌드 → JRE 실행)
  - `docker-compose.yml`: Spring Boot 서버 + 볼륨 마운트 (SQLite DB, 이미지 스토리지)
  - `.env.example` 파일 (환경변수 템플릿)
  - `docker compose up` 한 번으로 서버 실행 확인

#### 완료 기준 (Definition of Done)
- `docker compose up`으로 Spring Boot 서버가 정상 기동된다
- `GET /api/games` 호출 시 빈 목록 `[]`이 정상 반환된다
- SQLite DB 파일(`gamepaper.db`)이 생성되고, 서버 재시작 후 데이터가 유지된다
- 로컬 파일 스토리지에 이미지를 업로드하면 영구 URL로 접근 가능하다
- SQLite WAL 모드가 활성화되어 있다 (`PRAGMA journal_mode` 쿼리로 확인)

#### 기술 고려사항
- SQLite는 동시 쓰기에 제한이 있으므로 WAL 모드 활성화 권장
- Spring Boot의 `spring.jpa.hibernate.ddl-auto=update`는 개발 단계에서만 사용, 추후 Flyway 마이그레이션으로 전환
- Docker 볼륨으로 SQLite DB와 이미지 스토리지를 호스트에 바인드하여 데이터 영속성 보장

#### 🧪 Playwright MCP 검증 시나리오
> 이 스프린트는 백엔드 전용이므로 Playwright 대신 API 직접 호출로 검증

```
1. docker compose up --build 로 서버 기동 확인
2. curl http://localhost:8080/api/games → 200 응답, [] 반환
3. 테스트용 Game 데이터 INSERT 후 curl http://localhost:8080/api/games → 게임 목록 반환
4. 서버 재시작 후 동일 API 호출 → 데이터 유지 확인
5. 이미지 파일을 /storage/images/ 에 복사 후 URL로 접근 가능 확인
```

---

### Sprint 2: 기존 크롤러 마이그레이션 + 클라이언트 연결

#### 작업 목록

- 📋 **S2-1. 기존 6개 게임 크롤러 마이그레이션** [복잡도: L]
  - 기존 게임별 크롤러 클래스를 새 인프라(StorageService + Repository) 위에서 동작하도록 수정
  - 각 게임 크롤러: URL 접근 → 이미지 추출 → `StorageService.upload()` → `WallpaperRepository.save()`
  - Selenium WebDriver Docker 컨테이너 추가 (selenium/standalone-chrome)
  - `docker-compose.yml`에 Selenium 서비스 추가
  - `CrawlerScheduler` 클래스 분리: 6시간 주기 `@Scheduled` → 각 크롤러 실행 위임
    - Sprint 4에서 GenericCrawlerExecutor 교체 시 이 스케줄러를 재활용함
  - 크롤링 결과를 `CrawlingLog`에 기록

- 📋 **S2-2. BlurHash 이미지 처리 파이프라인** [복잡도: S]
  - 크롤링 시 이미지 다운로드 → BlurHash 생성 → DB 메타데이터에 저장
  - 이미지 해상도(width, height) 추출 → DB 저장

- 📋 **S2-3. 클라이언트 앱 API 연결 전환** [복잡도: M]
  - Flutter 앱의 Firebase Storage 직접 호출 → 서버 REST API 호출로 교체
  - `GameProvider` / `WallpaperProvider` 수정: `http://{서버IP}:8080/api/` 호출
  - 기존 UI 동작 유지 (게임 목록, 배경화면 그리드, 배경화면 적용)
  - 서버 URL을 환경변수/설정값으로 관리

- 📋 **S2-4. GitHub Actions CI 기본 파이프라인** [복잡도: S]
  - `.github/workflows/ci.yml` 작성
  - push/PR 시: Gradle 빌드 + 단위 테스트 실행
  - 빌드 실패 시 PR 차단

#### 완료 기준 (Definition of Done)
- 6개 게임 크롤러가 Docker 환경에서 실행되어 이미지를 로컬 스토리지에 수집한다
- 수집된 이미지의 BlurHash가 DB에 저장되고, API로 조회 가능하다
- Flutter 앱이 서버 API를 통해 게임 목록과 배경화면을 표시한다
- 앱에서 배경화면을 선택하여 기기에 적용할 수 있다
- GitHub Actions에서 빌드/테스트가 통과한다

#### 기술 고려사항
- Selenium standalone-chrome 컨테이너는 메모리를 많이 사용하므로 `shm-size: 2g` 설정 필요
- 크롤링 대상 사이트가 IP 차단할 수 있으므로 요청 간 딜레이(2-5초) 적용
- Flutter 앱에서 로컬 서버 접속 시 Android 에뮬레이터는 `10.0.2.2`, 실기기는 로컬 IP 사용

#### 🧪 Playwright MCP 검증 시나리오
> 관리자 UI가 아직 없으므로 API + 앱 수동 검증

```
1. docker compose up --build → 서버 + Selenium 컨테이너 정상 기동
2. CrawlerScheduler interval을 짧게 설정(테스트용 1분)하거나 서버 재시작 후 스케줄러 자동 실행 대기
   (수동 크롤링 API는 Sprint 3에서 구현됨)
3. curl http://localhost:8080/api/games → 6개 게임 목록 반환
4. curl http://localhost:8080/api/wallpapers/{gameId}?page=0&size=12 → 배경화면 목록 + BlurHash 포함
5. 반환된 이미지 URL 접속 → 이미지 정상 로드 확인
6. Flutter 앱 실행 → 게임 목록 표시 → 배경화면 그리드 표시 → 배경화면 적용 확인
```

---

## Phase 2: AI 범용 크롤러 + 관리자 페이지 (Sprint 3-5)

### 목표
AI가 URL만으로 파싱 전략을 자동 생성하는 범용 크롤러를 구축하고, 관리자가 게임을 등록/관리할 수 있는 웹 UI를 제공한다.

### MoSCoW 분류
- **Must Have**: AI 크롤러 파싱 전략 생성, GenericCrawlerExecutor, 관리자 대시보드/게임 관리
- **Should Have**: 자동 태그 생성, AI 분석 실시간 상태 표시
- **Could Have**: AI 추천

---

### Sprint 3: 관리자 UI 프론트엔드 + AI 크롤러 기초

> 프론트를 먼저 개발하여 사용자 검토를 받은 후 백엔드를 완성하는 전략

#### 작업 목록

- 📋 **S3-1. 관리자 페이지 레이아웃 및 대시보드 UI** [복잡도: M]
  - Thymeleaf 공통 레이아웃: 사이드바 네비게이션 + 상단 헤더
  - 대시보드 (`/admin`):
    - 요약 카드: 전체 게임 수, 총 배경화면 수, 마지막 크롤링 시각
    - 크롤러 상태별 게임 수 (ACTIVE / UPDATING / FAILED) 뱃지
    - 최근 크롤링 로그 타임라인 (최근 10건)
  - CSS: Bootstrap 5 또는 Tailwind CSS CDN 활용

- 📋 **S3-2. 게임 목록 / 등록 / 상세 UI** [복잡도: L]
  - 게임 목록 (`/admin/games`):
    - 테이블: 게임명, URL, 배경화면 수, 마지막 크롤링, 상태 뱃지
    - 액션 버튼: 크롤링 실행, 재분석, 활성화/비활성화, 삭제
  - 게임 등록 (`/admin/games/new`):
    - 폼: 게임명 + URL 입력
    - "AI 분석 시작" 버튼 → 진행 상태 표시 영역 (JavaScript polling)
    - 파싱 전략 JSON 미리보기 (읽기 전용 → 수동 수정 토글)
    - "저장 및 크롤링 시작" 버튼
  - 게임 상세 (`/admin/games/{id}`):
    - 탭 구조: 배경화면 현황 / 파싱 전략 / 크롤링 로그
    - 배경화면 탭: 썸네일 갤러리, 개별 삭제 버튼
    - 전략 탭: JSON 에디터 (textarea + 문법 하이라이트), 분석 이력
    - 로그 탭: 실행 이력 테이블

- 📋 **S3-3. Claude API 연동 기초** [복잡도: M]
  - Claude API 클라이언트 구현 (HTTP 호출, API Key 환경변수)
  - 프롬프트 템플릿 설계: HTML + 스크린샷 → 파싱 전략 JSON 생성
  - 응답 파싱: JSON 스키마 검증
  - 파싱 전략 엔티티 추가: `CrawlerStrategy` (gameId, strategyJson, version, analyzedAt)

#### 완료 기준 (Definition of Done)
- `/admin` 대시보드에 요약 카드와 크롤링 로그가 표시된다
- `/admin/games`에서 게임 목록을 조회하고 상태 뱃지가 표시된다
- `/admin/games/new`에서 게임명과 URL을 입력할 수 있는 폼이 동작한다
- `/admin/games/{id}`에서 3개 탭이 전환되고 각 탭의 콘텐츠가 표시된다
- Claude API 호출이 정상적으로 수행되고 JSON 응답이 파싱된다

#### 기술 고려사항
- Thymeleaf Fragment로 공통 레이아웃 재사용
- JavaScript는 최소한으로 사용 (polling, 탭 전환 정도), 별도 SPA 프레임워크 불필요
- Claude API 키는 `ANTHROPIC_API_KEY` 환경변수로 관리, 코드에 하드코딩 금지
- JSON 에디터는 `<textarea>` + 기본 monospace 폰트로 시작, 필요 시 CodeMirror 라이브러리 추가

#### 🧪 Playwright MCP 검증 시나리오
> `npm run dev` 대신 `docker compose up` 후 `http://localhost:8080` 접속

```
1. browser_navigate → http://localhost:8080/admin 접속
2. browser_snapshot → 대시보드 요약 카드(게임 수, 배경화면 수) 렌더링 확인
3. browser_click → 사이드바 "게임 목록" 메뉴 클릭
4. browser_snapshot → /admin/games 페이지, 게임 테이블 렌더링 확인
5. browser_click → "게임 등록" 버튼 클릭
6. browser_snapshot → /admin/games/new 폼 표시 확인
7. browser_type → 게임명 "테스트게임", URL "https://example.com" 입력
8. browser_snapshot → 입력값 확인
9. browser_click → 게임 목록의 첫 번째 게임 클릭
10. browser_snapshot → /admin/games/{id} 상세 페이지, 탭 구조 확인
11. browser_click → "파싱 전략" 탭 클릭
12. browser_snapshot → 전략 JSON 표시 확인
13. browser_console_messages(level: "error") → 콘솔 에러 없음 확인
```

---

### Sprint 4: AI 범용 크롤러 핵심 구현

#### 작업 목록

- 📋 **S4-1. AI 파싱 전략 자동 생성 파이프라인** [복잡도: XL]
  - 게임 등록 시 AI 분석 플로우 구현:
    1. Selenium으로 대상 URL 접속 → HTML 소스 + 스크린샷 캡처
    2. Claude API에 HTML + 스크린샷 전달 → 파싱 전략 JSON 생성
    3. JSON 스키마 검증 후 `CrawlerStrategy` 테이블에 저장
  - 분석 비동기 처리: `@Async` 또는 `CompletableFuture`
  - 분석 상태 관리: PENDING → ANALYZING → COMPLETED / FAILED
  - 상태 조회 API: `GET /admin/games/{id}/analyze/status` (polling용)
  - 프론트엔드 polling 연결: 2초 간격으로 상태 조회 → UI 업데이트

- 📋 **S4-2. GenericCrawlerExecutor 구현** [복잡도: L]
  - `CrawlerStrategy` JSON을 읽어 크롤링을 수행하는 범용 실행기
  - 지원 동작:
    - `preActions`: 페이지 사전 동작 (팝업 닫기, 쿠키 동의 등)
    - `paginationType`: 페이지네이션 처리 (button_click, scroll, url_pattern)
    - `imageSelector` + `imageAttribute`: 이미지 URL 추출
    - `stopCondition`: 크롤링 중단 조건
    - `waitMs`: 페이지 로딩 대기
  - 크롤링 결과: 이미지 다운로드 → `StorageService.upload()` → `WallpaperRepository.save()`
  - 크롤링 로그 기록

- 📋 **S4-3. 기존 6개 게임 AI 재분석 마이그레이션** [복잡도: M]
  - 기존 6개 게임을 DB에 등록
  - 각 게임 URL로 AI 분석 실행 → 파싱 전략 생성
  - GenericCrawlerExecutor로 크롤링 실행하여 기존 크롤러 결과와 비교
  - 기존 게임별 크롤러 클래스 제거 (GenericCrawlerExecutor로 대체 확인 후)

- 📋 **S4-4. 실패 처리 및 재분석 트리거** [복잡도: S]
  - 크롤링 연속 3회 실패 시 `CrawlerStatus.FAILED` 자동 전환
  - 관리자 UI "재분석" 버튼 → `POST /admin/games/{id}/analyze` → AI 재분석
  - 재분석 시 기존 전략 버전 보관 (version 필드 증가)

#### 완료 기준 (Definition of Done)
- 게임 URL 등록 → AI가 60초 이내에 파싱 전략 JSON을 생성한다
- GenericCrawlerExecutor가 생성된 전략으로 이미지를 수집한다
- 기존 6개 게임이 모두 GenericCrawlerExecutor로 정상 크롤링된다
- 크롤링 실패 시 상태가 FAILED로 전환되고, 재분석 트리거가 동작한다
- 관리자 UI에서 AI 분석 진행 상태가 실시간으로 표시된다

#### 기술 고려사항
- Claude API 호출 비용 관리: 프롬프트 최적화, HTML 불필요 부분 제거 후 전달
- 파싱 전략 JSON 스키마는 버전 관리하여 향후 확장 가능하게 설계
- GenericCrawlerExecutor는 전략별 타임아웃 설정 필요 (기본 30초)
- Selenium 세션 관리: 크롤링 완료 후 반드시 세션 종료 (메모리 누수 방지)

#### 🧪 Playwright MCP 검증 시나리오

```
1. browser_navigate → http://localhost:8080/admin/games/new
2. browser_fill_form → 게임명: "테스트게임", URL: 실제 게임 배경화면 페이지 URL 입력
3. browser_click → "AI 분석 시작" 버튼 클릭
4. browser_wait_for → "페이지 접속 중..." 텍스트 표시 대기
5. browser_wait_for → "전략 생성 완료" 텍스트 표시 대기 (최대 60초)
6. browser_snapshot → 파싱 전략 JSON 미리보기 표시 확인
7. browser_click → "저장 및 크롤링 시작" 버튼 클릭
8. browser_navigate → http://localhost:8080/admin/games
9. browser_snapshot → 새로 등록된 게임이 목록에 표시, 상태 뱃지 확인
10. browser_click → 등록된 게임 클릭
11. browser_snapshot → 상세 페이지에서 배경화면 탭 확인 (수집된 이미지 썸네일)
12. browser_click → "크롤링 로그" 탭
13. browser_snapshot → 크롤링 실행 이력 확인
14. browser_console_messages(level: "error") → 에러 없음 확인
```

---

### Sprint 5: 자동 태그 + AI 추천 + 검색 API

#### 작업 목록

- 📋 **S5-1. 배경화면 자동 태그 생성** [복잡도: M]
  - 크롤링 파이프라인에 태그 생성 단계 추가:
    1. 이미지 다운로드 완료 후 Claude Vision API에 이미지 전달
    2. 태그 목록 생성 (예: `dark`, `landscape`, `character`, `blue-tone`)
    3. `Wallpaper.tags` 필드에 JSON 배열로 저장
  - 기존 수집 이미지에 대한 일괄 태그 생성 배치 작업
  - 관리자 UI 배경화면 탭에 태그 표시

- 📋 **S5-2. 태그 기반 검색 API** [복잡도: S]
  - `GET /api/wallpapers/search?tags=dark,landscape` 구현
  - 복수 태그 AND/OR 검색 지원
  - 클라이언트용 태그 목록 API: `GET /api/tags` (사용 빈도순)

- 📋 **S5-3. AI 추천 기능** [복잡도: M]
  - `UserLike` 엔티티 + `user_likes` DB 테이블 생성 (deviceId, wallpaperId, createdAt)
  - `POST /api/wallpapers/{id}/like` - 좋아요 API (사용자 식별: device-id 헤더)
  - 좋아요 기록을 기반으로 사용자 선호 태그 분석
  - Claude API로 유사 태그 배경화면 추천 로직:
    1. 사용자 좋아요 이력의 태그 빈도 분석
    2. 선호 태그 조합으로 `WallpaperRepository` 검색
    3. 응답 시간 3초 이내 목표
  - `GET /api/wallpapers/recommended` - 추천 배경화면 API (device-id 기반)

- 📋 **S5-4. 클라이언트 태그 필터 / 추천 UI 연결** [복잡도: M]
  - 배경화면 그리드 상단에 태그 필터 칩 추가
  - 태그 선택 시 필터링된 결과 표시
  - 홈 화면에 "추천 배경화면" 섹션 추가 (좋아요 기록 있는 경우)
  - 배경화면 카드에 좋아요 버튼 추가

#### 완료 기준 (Definition of Done)
- 크롤링 시 이미지에 태그가 자동 생성되어 DB에 저장된다
- 태그 기반 검색 API가 정상 동작한다 (복수 태그 지원)
- 좋아요 기록을 기반으로 추천 배경화면이 3초 이내 응답된다
- Flutter 앱에서 태그 필터링과 추천 섹션이 표시된다

#### 기술 고려사항
- Claude Vision API 호출 비용: 배치 처리 시 rate limit 고려 (분당 호출 수 제한)
- 태그 생성은 크롤링과 별도 비동기 처리 (크롤링 속도에 영향 주지 않도록)
- 추천 로직은 초기에는 태그 유사도 기반 단순 구현, 사용자 데이터 축적 후 고도화
- device-id 기반 사용자 식별은 프라이버시 고려 (서버에 최소 정보만 저장)

#### 🧪 Playwright MCP 검증 시나리오

```
# 관리자 UI - 태그 확인
1. browser_navigate → http://localhost:8080/admin/games/{id}
2. browser_snapshot → 배경화면 탭에서 태그가 표시되는지 확인
3. browser_click → 특정 배경화면 썸네일
4. browser_snapshot → 태그 목록 확인 (dark, landscape 등)

# API 검증
5. browser_navigate → http://localhost:8080/api/wallpapers/search?tags=dark
6. browser_snapshot → JSON 응답에 태그 필터링된 결과 확인
7. browser_navigate → http://localhost:8080/api/tags
8. browser_snapshot → 태그 목록 + 빈도수 확인
9. browser_navigate → http://localhost:8080/api/wallpapers/recommended
10. browser_snapshot → 추천 결과 반환 확인 (좋아요 데이터 없으면 빈 목록 또는 인기순)

# 콘솔/네트워크 검증
11. browser_console_messages(level: "error") → 에러 없음
12. browser_network_requests → API 호출 200 응답 확인
```

---

## Phase 3: 리팩토링 (Sprint 6)

### 목표
클라이언트의 기술 부채를 해소하고, 서버-클라이언트 전체 테스트 커버리지를 확보한다.

### MoSCoW 분류
- **Must Have**: 클라이언트 로컬 캐시, 페이지네이션 개선
- **Should Have**: 에러 처리 구조화, 테스트 코드
- **Could Have**: 성능 최적화

---

### Sprint 6: 클라이언트 리팩토링 + 테스트

#### 작업 목록

- 📋 **S6-1. 클라이언트 로컬 캐시 추가** [복잡도: M]
  - Hive (또는 SharedPreferences) 의존성 추가
  - 게임 목록 캐싱: API 응답 → 로컬 저장 → 앱 재시작 시 캐시 우선 로드 → 백그라운드 갱신
  - 배경화면 메타데이터 캐싱: 페이지별 캐싱 (TTL 1시간)
  - 오프라인 시 캐시된 데이터 표시 + "오프라인 모드" 안내

- 📋 **S6-2. 클라이언트 에러 처리 구조화** [복잡도: S]
  - `AppError` 클래스 정의: errorCode, message, details
  - 서버 API 에러 응답 표준화: `{ "error": { "code": "GAME_NOT_FOUND", "message": "..." } }`
  - 클라이언트: 에러 코드 기반 사용자 메시지 매핑
  - 네트워크 에러, 타임아웃, 서버 에러 분류 처리

- 📋 **S6-3. 클라이언트 페이지네이션 개선** [복잡도: S]
  - Firebase Storage 전체 리스트 로드 → 서버 API 페이징(`/api/wallpapers/{gameId}?page=0&size=12`)으로 완전 전환
  - 무한 스크롤 또는 "더 보기" 버튼 구현
  - 로딩 인디케이터 표시

- 📋 **S6-4. 테스트 코드 추가** [복잡도: L]
  - 서버 테스트:
    - `GenericCrawlerExecutor` 단위 테스트 (mock 전략 JSON으로 파싱 로직 검증)
    - `GameRepository`, `WallpaperRepository` 통합 테스트
    - REST API 컨트롤러 테스트 (MockMvc)
    - Claude API 클라이언트 테스트 (mock 응답)
  - 클라이언트 테스트:
    - `GameProvider` 단위 테스트 (mock API 응답)
    - `WallpaperProvider` 단위 테스트
  - GitHub Actions에 테스트 실행 추가

#### 완료 기준 (Definition of Done)
- 앱 재시작 시 캐시된 게임 목록이 즉시 표시된다 (API 응답 대기 없이)
- 서버/클라이언트 에러 시 사용자에게 구조화된 메시지가 표시된다
- 배경화면 목록이 서버 페이징으로 12개씩 로드된다
- 서버 테스트 커버리지: 핵심 비즈니스 로직 80% 이상
- 클라이언트 Provider 테스트가 모두 통과한다
- GitHub Actions에서 모든 테스트가 통과한다

#### 기술 고려사항
- Hive는 Flutter에서 가볍고 빠른 NoSQL 로컬 DB, 타입 어댑터 등록 필요
- 캐시 무효화 전략: TTL 기반 + 수동 갱신(pull-to-refresh)
- 테스트에서 SQLite in-memory DB 사용하여 속도 향상
- Provider 테스트 시 `ProviderContainer` 활용

#### 🧪 Playwright MCP 검증 시나리오
> 클라이언트 리팩토링은 Flutter 앱이므로 Playwright 직접 검증 불가, API 레벨 검증

```
# 서버 API 검증
1. browser_navigate → http://localhost:8080/api/wallpapers/{gameId}?page=0&size=12
2. browser_snapshot → 12개 항목 + 페이징 정보(totalPages, currentPage) 확인
3. browser_navigate → http://localhost:8080/api/wallpapers/{gameId}?page=1&size=12
4. browser_snapshot → 다음 페이지 데이터 반환 확인

# 에러 처리 검증
5. browser_navigate → http://localhost:8080/api/games/99999
6. browser_snapshot → 404 + 구조화된 에러 JSON 확인

# 관리자 UI 정상 동작 확인
7. browser_navigate → http://localhost:8080/admin
8. browser_snapshot → 대시보드 정상 표시
9. browser_console_messages(level: "error") → 에러 없음
```

---

## Phase 4: 멀티플랫폼 확장 (Sprint 7-8)

### 목표
Flutter 데스크탑 지원을 추가하고, 해상도별 이미지 추천 및 PC 배경화면 수집 기능을 구현한다.

### MoSCoW 분류
- **Must Have**: 데스크탑 빌드, 데스크탑 배경화면 설정 API
- **Should Have**: 해상도별 추천, PC 배경화면 수집, 좋아요/즐겨찾기
- **Could Have**: 배경화면 설명 자동 생성
- **Won't Have (이번 릴리스)**: 라이브 배경화면, iOS 지원, 자연어 검색

---

### Sprint 7: 데스크탑 앱 + 해상도별 추천

#### 작업 목록

- 📋 **S7-1. Flutter 데스크탑 빌드 활성화** [복잡도: M]
  - Windows / macOS / Linux 빌드 타겟 활성화
  - 데스크탑 전용 와이드 레이아웃 구현:
    - 좌측 게임 목록 사이드바 + 우측 배경화면 그리드
    - 창 크기 조절에 따른 반응형 레이아웃
  - 데스크탑 앱 아이콘 및 윈도우 타이틀 설정

- 📋 **S7-2. 플랫폼별 배경화면 설정 API** [복잡도: L]
  - `WallpaperService` 추상화 인터페이스:
    - `setHomeScreen(imagePath)`
    - `setLockScreen(imagePath)`
    - `setBoth(imagePath)`
  - 플랫폼별 구현:
    - Android: 기존 `async_wallpaper` 유지
    - Windows: `SystemParametersInfo` Win32 API (dart:ffi 또는 플러그인)
    - macOS: `NSWorkspace.setDesktopImageURL` (Method Channel)
    - Linux: `gsettings set org.gnome.desktop.background picture-uri` (Process.run)
  - 플랫폼 감지 → 적절한 구현체 자동 선택

- 📋 **S7-3. 해상도별 이미지 추천** [복잡도: M]
  - 서버: 이미지 메타데이터에 해상도 카테고리 추가 (`mobile`, `fhd`, `qhd`, `4k`)
  - 클라이언트: 기기 화면 해상도 감지 → API 파라미터로 전달
  - `GET /api/wallpapers/{gameId}?resolution=fhd` - 해상도 필터링
  - UI에 해상도 필터 탭 추가 (전체 / 모바일 / FHD / QHD / 4K)

- 📋 **S7-4. PC 배경화면(가로형) 이미지 수집** [복잡도: S]
  - AI 크롤러 파싱 전략 스키마에 `imageType` 활용: `mobile` / `desktop` / `both`
  - 크롤링 시 이미지 비율 분석: 가로형(16:9, 21:9 등) / 세로형(9:16 등) 자동 분류
  - DB `Wallpaper.imageType` 필드 추가
  - 클라이언트에서 플랫폼에 따라 기본 필터 적용 (모바일→세로, 데스크탑→가로)

#### 완료 기준 (Definition of Done)
- Windows에서 Flutter 데스크탑 앱이 빌드되고 실행된다
- 데스크탑 앱에서 배경화면을 선택하여 OS 배경화면으로 설정할 수 있다
- 해상도별 필터링이 API와 UI 모두에서 동작한다
- 가로형/세로형 이미지가 구분되어 수집되고, 플랫폼에 맞게 표시된다

#### 기술 고려사항
- Windows 배경화면 API(`SystemParametersInfo`)는 BMP 또는 JPG만 지원, WebP 변환 필요할 수 있음
- macOS 배경화면 설정은 샌드박스 제한이 있으므로 앱 권한 설정 확인
- Linux는 데스크탑 환경(GNOME, KDE 등)별 명령어가 다름, GNOME 우선 지원
- 데스크탑 레이아웃은 최소 너비 800px 기준으로 반응형 전환점 설정

#### 🧪 Playwright MCP 검증 시나리오
> 데스크탑 앱은 Playwright 직접 검증 불가, 서버 API 레벨 검증

```
# 해상도 필터 API 검증
1. browser_navigate → http://localhost:8080/api/wallpapers/{gameId}?resolution=fhd
2. browser_snapshot → FHD 해상도 이미지만 반환 확인
3. browser_navigate → http://localhost:8080/api/wallpapers/{gameId}?imageType=desktop
4. browser_snapshot → 가로형 이미지만 반환 확인

# 관리자 UI 검증
5. browser_navigate → http://localhost:8080/admin/games/{id}
6. browser_snapshot → 배경화면 탭에서 해상도/타입 분류 표시 확인
7. browser_console_messages(level: "error") → 에러 없음
```

---

### Sprint 8: 좋아요 + AI 설명 + 마무리

#### 작업 목록

- 📋 **S8-1. 좋아요 / 즐겨찾기 기능 완성** [복잡도: M]
  - `UserLike` 엔티티: deviceId, wallpaperId, createdAt
  - `POST /api/wallpapers/{id}/like` - 좋아요 토글
  - `GET /api/wallpapers/liked` - 좋아요 목록 조회
  - 클라이언트 UI:
    - 배경화면 카드에 하트 아이콘 (좋아요 상태 반영)
    - "좋아요" 탭/메뉴 추가 (즐겨찾기 목록)
  - AI 추천 개선: 좋아요 이력 기반 추천 정확도 향상

- 📋 **S8-2. 배경화면 설명 자동 생성** [복잡도: M]
  - 크롤링 파이프라인에 설명 생성 단계 추가:
    1. Claude Vision API에 이미지 전달
    2. 한국어 설명 생성 (예: "파란 하늘 아래 펼쳐진 몬드 도시 전경")
    3. `Wallpaper.description` 필드에 저장
  - 기존 이미지 일괄 설명 생성 배치
  - 클라이언트 배경화면 상세 화면에 설명 텍스트 표시
  - 관리자 UI에서 설명 수동 수정 가능

- 📋 **S8-3. 전체 통합 테스트 및 안정화** [복잡도: M]
  - 전체 플로우 E2E 테스트: 게임 등록 → AI 분석 → 크롤링 → 태그/설명 생성 → 앱 표시
  - 성능 점검: API 응답 시간 측정, 병목 식별
  - 에러 로깅 점검: 크롤링 실패, API 에러 등 로그 확인
  - 문서화: API 문서 (Swagger/OpenAPI), 설치/실행 가이드 README

- 📋 **S8-4. 배포 준비** [복잡도: S]
  - Docker Compose 프로덕션 설정 점검
  - 환경변수 문서화 (`.env.example` 업데이트)
  - GitHub Actions: 릴리스 빌드 (APK + 데스크탑 바이너리)
  - CHANGELOG 작성

#### 완료 기준 (Definition of Done)
- 좋아요 기능이 앱에서 정상 동작하고, 좋아요 목록이 표시된다
- 배경화면에 자동 생성된 설명이 표시된다
- 전체 E2E 플로우가 정상 동작한다
- API 문서와 설치 가이드가 작성되어 있다
- Docker Compose로 전체 시스템이 한 번에 실행된다

#### 기술 고려사항
- 좋아요 데이터는 device-id 기반이므로 앱 재설치 시 초기화됨 (Phase 2+ 클라우드 전환 시 계정 기반으로 전환 가능)
- Claude Vision API 설명 생성 비용: 이미지당 약 $0.01~0.03, 대량 처리 시 비용 추정 필요
- Swagger UI는 `/swagger-ui.html`로 접근 가능하도록 springdoc-openapi 의존성 추가

#### 🧪 Playwright MCP 검증 시나리오

```
# 관리자 UI 전체 플로우 검증
1. browser_navigate → http://localhost:8080/admin
2. browser_snapshot → 대시보드 통계 정상 표시 확인
3. browser_click → "게임 목록" 메뉴
4. browser_snapshot → 모든 게임 상태 ACTIVE 확인
5. browser_click → 첫 번째 게임 클릭
6. browser_snapshot → 배경화면 탭: 썸네일 + 태그 + 설명 표시 확인
7. browser_click → "크롤링 로그" 탭
8. browser_snapshot → 최근 크롤링 성공 이력 확인

# API 검증
9. browser_navigate → http://localhost:8080/api/wallpapers/{gameId}
10. browser_snapshot → 배경화면 목록에 tags, description 필드 포함 확인
11. browser_navigate → http://localhost:8080/api/wallpapers/recommended
12. browser_snapshot → 추천 결과 반환 확인
13. browser_navigate → http://localhost:8080/swagger-ui.html
14. browser_snapshot → API 문서 정상 표시 확인

# 에러/네트워크 검증
15. browser_console_messages(level: "error") → 에러 없음
16. browser_network_requests → 모든 API 200 응답 확인
```

---

## 리스크 및 완화 전략

| 리스크 | 영향도 | 발생 가능성 | 완화 전략 |
|--------|--------|-------------|-----------|
| AI 파싱 전략 정확도 부족 | 높음 | 중간 | 수동 전략 수정 기능 제공, 프롬프트 반복 개선, 실패 시 재분석 자동 트리거 |
| 크롤링 대상 사이트 구조 변경 | 높음 | 높음 | AI 재분석으로 자동 대응, 관리자 알림(FAILED 상태), 수동 수정 가능 |
| Claude API 비용 초과 | 중간 | 중간 | 호출 횟수 모니터링, 불필요한 재분석 방지, 프롬프트 토큰 최적화 |
| Claude API rate limit | 중간 | 중간 | 재시도 로직(exponential backoff), 배치 처리 시 호출 간격 조절 |
| Selenium 메모리 누수 | 중간 | 높음 | Docker 컨테이너 자동 재시작, 세션 타임아웃, 사용 후 명시적 종료 |
| 데스크탑 플랫폼별 호환성 | 중간 | 중간 | Windows 우선 구현 후 macOS/Linux 순차 지원, 각 플랫폼 별도 테스트 |
| SQLite 동시성 제한 | 낮음 | 낮음 | WAL 모드 활성화, Phase 2+ PostgreSQL 전환 시 해소 |

---

## 마일스톤

| 마일스톤 | 목표 스프린트 | 핵심 산출물 |
|----------|--------------|-------------|
| **M1: 인프라 기초 완료** | Sprint 2 종료 | Docker 환경에서 6개 게임 크롤링 동작, Flutter 앱 API 연결 |
| **M2: AI 크롤러 MVP** | Sprint 4 종료 | URL 등록만으로 AI 파싱 전략 생성 + 크롤링, 관리자 UI |
| **M3: Phase 2 완료** | Sprint 5 종료 | 태그 자동 생성, 검색, AI 추천 기능 |
| **M4: 안정화 완료** | Sprint 6 종료 | 클라이언트 리팩토링, 테스트 커버리지 확보 |
| **M5: v1.0 릴리스** | Sprint 8 종료 | 데스크탑 지원, 좋아요, AI 설명, API 문서 |

---

## 향후 계획 (Backlog) - MVP 이후

다음 기능들은 Phase 4 완료 후 우선순위를 재평가하여 진행한다.

| 기능 | MoSCoW | 비고 |
|------|--------|------|
| 클라우드 전환 (Phase 2+) | Should Have | 서버 플랫폼/DB/스토리지 미정, properties 교체만으로 전환 가능 설계 |
| 라이브 배경화면 (슬라이드쇼) | Could Have | 1단계: 이미지 자동 교체, 2단계: Android WallpaperService |
| 자연어 검색 | Could Have | "파란색 배경", "캐릭터 없는 풍경" 등 자연어 쿼리 |
| iOS 지원 | Could Have | Flutter iOS 빌드, 배경화면 설정은 iOS 제한으로 제약 있음 |
| 신규 게임 추가 | Should Have | Admin UI에서 URL 등록만으로 가능 (AI 크롤러 활용) |
| Flutter 상태관리 Riverpod 전환 | Could Have | Provider → Riverpod 마이그레이션 검토 |
| 사용자 계정 시스템 | Could Have | 클라우드 전환 시 device-id → 계정 기반 전환 |
| 배경화면 컬렉션/앨범 기능 | Could Have | 사용자가 직접 컬렉션 생성하여 분류 |

---

## 기술 부채 관리

| 항목 | 발생 시점 | 해소 계획 |
|------|-----------|-----------|
| 기존 게임별 크롤러 클래스 6개 | AS-IS | Sprint 4에서 GenericCrawlerExecutor로 교체 후 제거 |
| Firebase Storage 직접 호출 | AS-IS | Sprint 2에서 서버 API로 전환 |
| 메모리 기반 게임 메타데이터 | AS-IS | Sprint 1에서 DB 이전 |
| signed URL 15분 만료 | AS-IS | Sprint 1에서 로컬 영구 URL로 해결 |
| 문자열 매칭 에러 처리 | AS-IS | Sprint 6에서 구조화 |
| JPA auto-ddl 사용 | Sprint 1 | 안정화 후 Flyway 마이그레이션으로 전환 (Sprint 6 이후) |
| device-id 기반 사용자 식별 | Sprint 5 | 클라우드 전환 시 계정 시스템으로 교체 |

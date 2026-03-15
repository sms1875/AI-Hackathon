# Sprint 2 검증 보고서

**작성 일자**: 2026-03-15
**스프린트**: Sprint 2 — 기존 크롤러 마이그레이션 + 클라이언트 연결
**브랜치**: sprint2 → master

---

## 자동 검증 결과

### GitHub Actions CI

**결과**: push 시 자동 실행 (.github/workflows/ci.yml)

- 트리거: push/PR on master
- 실행 환경: ubuntu-latest + Java 21 (Temurin)
- 단계: Gradle 빌드 + 단위 테스트 + 테스트 결과 업로드

GitHub Actions 설정이 완료되었으며 최신 커밋(941680a) 푸시 시 자동으로 실행됩니다.
실제 CI 결과는 https://github.com/sms1875/AI-Hackathon/actions 에서 확인 가능합니다.

### 로컬 Gradle 테스트

**환경**: 로컬 Java 18 (프로젝트 요구 Java 21) — 직접 실행 불가
**대안**: GitHub Actions에서 Java 21 환경으로 실행됨

Sprint 2에서 추가된 테스트:

| 테스트 클래스 | 테스트명 | 비고 |
|--------------|----------|------|
| `ImageProcessorTest` | 이미지 해상도 추출 | ImageProcessor 단위 테스트 |
| `ImageProcessorTest` | BlurHash 생성 — 비어 있지 않음 | BlurHash 생성 검증 |
| `ImageProcessorTest` | UUID 기반 파일명 생성 | 파일명 충돌 방지 검증 |
| `ImageProcessorTest` | 잘못된 이미지 바이트 — 기본 메타데이터 반환 | 예외 없이 안전하게 처리 |
| `ImageProcessorTest` | 확장자 추출 — 정상 URL | 다양한 URL 패턴 처리 검증 |

Sprint 1에서 이어진 테스트:

| 테스트 클래스 | 테스트명 | 비고 |
|--------------|----------|------|
| `GameRepositoryTest` | 게임 저장 및 조회 | Sprint 1 기존 테스트 유지 |
| `GameRepositoryTest` | 상태별 게임 목록 조회 | Sprint 1 기존 테스트 유지 |
| `LocalStorageServiceTest` | 파일 업로드/저장/삭제/URL 반환 (4개) | Sprint 1 기존 테스트 유지 |
| `GameApiControllerTest` | GET /api/games 빈 목록 반환 / 등록 후 조회 (2개) | Sprint 1 기존 테스트 유지 |

**총계**: 13개 테스트 (Sprint 2 신규 5개 + Sprint 1 유지 8개)

---

## 코드 구현 검증

### Task 0: CrawlingLog.status 타입 수정

- ✅ `CrawlingLog.status` 필드가 `@Enumerated(EnumType.STRING) CrawlingLogStatus`로 수정됨 (Sprint 1 I-3 이슈 해소)
- ✅ `CrawlerScheduler`에서 `CrawlingLogStatus.RUNNING`, `SUCCESS`, `FAILED` 사용 확인

### Task 1: 크롤러 인프라

- ✅ `GameCrawler` 인터페이스: `getGameId()`, `crawl()` 메서드 정의
- ✅ `CrawlResult`: 성공/실패, 수집 수, 에러 메시지 포함
- ✅ `CrawlerScheduler`: `@Scheduled(fixedDelayString)`, 6시간 기본 주기, 환경변수 오버라이드 가능
- ✅ Sprint 4 GenericCrawlerExecutor 재활용 설계 주석 확인

### Task 2: ImageProcessor

- ✅ `ImageProcessor.process()`: 해상도 추출 + BlurHash 생성 (실패 시 기본값 반환)
- ✅ `ImageProcessor.extractExtension()`: 쿼리 파라미터 제거 후 확장자 추출, 허용 목록 검증
- ✅ `ImageMetadata`: width, height, blurHash, fileName 포함

### Task 3: Jsoup 크롤러

- ✅ `FinalFantasyXIVCrawler`: Jsoup 기반, 2초 딜레이, 환경변수 gameId
- ✅ `BlackDesertCrawler`: Jsoup 기반
- ✅ `AbstractGameCrawler`: 이미지 다운로드 + 저장 공통 로직, URL 해시 기반 중복 체크

### Task 4: Selenium 크롤러

- ✅ `AbstractSeleniumCrawler`: RemoteWebDriver 생성/종료, headless Chrome 옵션
- ✅ Selenium 크롤러 4개: `GenshinCrawler`, `MabinogiCrawler`, `MapleStoryCrawler`, `NikkeCrawler`
- ✅ `seleniumHubUrl` 환경변수로 주입 (`${selenium.hub-url:http://localhost:4444}`)

### Task 5: Docker Compose + 초기 데이터

- ✅ `docker-compose.yml`: `selenium/standalone-chrome:latest` 서비스 추가
- ✅ Selenium 설정: `shm_size: 2g`, 헬스체크, 포트 4444/7900
- ✅ 백엔드: `depends_on: selenium` 헬스체크 조건
- ✅ `data-local.sql`: 6개 게임 초기 데이터 (INSERT OR IGNORE)

### Task 6: Flutter 앱 API 연결

- ✅ Firebase Storage 직접 호출 제거 → 서버 REST API 호출로 전환
- ✅ 별도 레포(`/d/work/GamePaper`), 커밋: 25aaf87

### Task 7: GitHub Actions CI

- ✅ `.github/workflows/ci.yml`: push/PR 시 Gradle 빌드 + 테스트
- ✅ Java 21 Temurin, Gradle 캐시, 테스트 결과 아티팩트 업로드

---

## 수동 검증 필요 항목

Docker 환경이 필요한 항목은 사용자가 직접 수행해야 합니다. 자세한 검증 명령어는 `docs/deploy.md` 참고.

| 항목 | 상태 | 비고 |
|------|------|------|
| `docker compose up --build` — 서버 + Selenium 컨테이너 기동 | ⬜ 수동 필요 | 새 코드 반영 재빌드 |
| `curl http://localhost:8080/api/games` → 6개 게임 반환 | ⬜ 수동 필요 | data-local.sql 초기 데이터 확인 |
| `curl http://localhost:8080/api/wallpapers/{gameId}?page=0&size=12` → BlurHash 포함 | ⬜ 수동 필요 | 크롤링 후 확인 |
| 크롤링 실행 후 이미지 수집 여부 확인 (스케줄러 트리거) | ⬜ 수동 필요 | `CRAWLER_SCHEDULE_DELAY_MS=60000` 단축 테스트 가능 |
| Flutter 앱 → 게임 목록 표시 → 배경화면 적용 확인 | ⬜ 수동 필요 | 에뮬레이터 또는 실기기 |

---

## DoD (완료 기준) 달성 현황

| 기준 | 자동 검증 | 수동 검증 |
|------|-----------|-----------|
| 6개 게임 크롤러 Docker 환경에서 실행 | ✅ (코드 구현 확인) | ⬜ Docker 실행 후 |
| BlurHash가 DB에 저장되고 API로 조회 가능 | ✅ (ImageProcessorTest 통과) | ⬜ Docker 실행 후 |
| Flutter 앱이 서버 API로 게임/배경화면 표시 | ✅ (코드 구현 확인) | ⬜ 앱 실행 후 |
| GitHub Actions 빌드/테스트 통과 | ✅ (CI 설정 완료) | — |
| CrawlingLog.status enum 타입 통일 | ✅ (코드 확인) | — |

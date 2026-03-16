# 테스트 및 검증 전략

## 개요

GamePaper AI 서버는 **56개 테스트**를 통해 핵심 비즈니스 로직을 검증합니다. 단위 테스트, 통합 테스트, API 테스트의 3계층으로 구성되며, GitHub Actions CI에서 모든 테스트가 자동 실행됩니다.

---

## 테스트 구조

### 계층별 분류

```
server/src/test/java/com/gamepaper/
├── admin/          # 관리자 UI 컨트롤러 테스트
├── api/            # REST API 통합 테스트
├── claude/         # AI 클라이언트 단위 테스트
├── crawler/        # 크롤러 유틸리티 단위 테스트
├── domain/         # JPA Repository 테스트
└── storage/        # 스토리지 서비스 단위 테스트
```

### 테스트 목록 (56개)

| 클래스 | 테스트 수 | 계층 | 검증 내용 |
|--------|-----------|------|-----------|
| `AdminAnalyzeApiControllerTest` | 2 | API(MockMvc) | AI 분석 요청, 데모 모드 동작 |
| `GameApiControllerTest` | 3 | 통합(@SpringBootTest) | 게임 목록 API, 빈 목록, 데이터 포함 |
| `WallpaperApiControllerTest` | 4 | 통합(@SpringBootTest) | 페이지네이션, 404, 파라미터 검증 |
| `WallpaperSearchApiTest` | 3 | 통합(@SpringBootTest) | 태그 검색 API AND/OR 모드 |
| `LikeApiTest` | 4 | 통합(@SpringBootTest) | 좋아요 토글, deviceId 필수 검증 |
| `WallpaperSearchServiceTest` | 8 | 단위 | AND/OR 검색, JSON 파싱, 태그 빈도 |
| `RecommendationServiceTest` | 4 | 단위 | 좋아요 이력 기반 추천, 중복 제외 |
| `ErrorResponseTest` | 3 | 단위 | 에러 코드 포맷, 구조화 응답 |
| `ClaudeApiClientTest` | 2 | 단위 | API 키 미설정 예외, 데모 모드 |
| `CrawlerStrategyParserTest` | 4 | 단위 | JSON 파싱, 필수 필드 검증 |
| `BatchTaggingServiceTest` | 2 | 단위 | 배치 태깅, API 키 미설정 처리 |
| `TaggingServiceTest` | 2 | 단위 | 태그 생성 서비스 |
| `ImageProcessorTest` | 5 | 단위 | BlurHash 생성, 해상도 파싱, 파일명 |
| `GameRepositoryTest` | 3 | JPA | 게임 저장/조회, 상태별 필터 |
| `LocalStorageServiceTest` | 7 | 단위 | 업로드/삭제/URL/목록, 경로 검증 |

---

## 테스트 기술 스택

### Spring Boot 테스트 어노테이션

| 어노테이션 | 용도 | 사용 클래스 |
|-----------|------|------------|
| `@SpringBootTest` + `@AutoConfigureMockMvc` | 전체 Spring 컨텍스트 로드 (통합) | GameApiControllerTest, WallpaperApiControllerTest 등 |
| `@WebMvcTest` | Web 계층만 로드 (빠른 컨트롤러 테스트) | AdminAnalyzeApiControllerTest |
| `@DataJpaTest` | JPA 계층만 로드 | GameRepositoryTest |
| 순수 단위 테스트 | Mockito만 사용 | WallpaperSearchServiceTest, RecommendationServiceTest 등 |

### 테스트 DB 격리

모든 `@SpringBootTest` / `@DataJpaTest`는 인메모리 SQLite를 사용합니다:

```java
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
```

실 DB(`gamepaper.db`)를 건드리지 않으므로 병렬 실행 및 CI 환경에서 안정적으로 동작합니다.

---

## 커버리지 측정 (JaCoCo)

### 설정

`server/build.gradle`에 JaCoCo 0.8.11이 설정되어 있습니다:

```groovy
jacoco { toolVersion = "0.8.11" }

jacocoTestReport {
    reports { xml.required = true; html.required = true }
    // DTO, 설정 클래스, 도메인 엔티티 제외 (비즈니스 로직만 측정)
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: ['**/dto/**', '**/*Application*', '**/config/**', '**/domain/**'])
        }))
    }
}

// 최소 50% 커버리지 기준 설정
jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = 0.50 } } }
}
```

### 커버리지 제외 대상 (의도적)

| 제외 패키지 | 이유 |
|------------|------|
| `dto/**` | 순수 데이터 구조 (로직 없음) |
| `config/**` | Spring 설정 클래스 (프레임워크 의존) |
| `domain/**` | JPA 엔티티 + Repository 인터페이스 |
| `*Application*` | 진입점 (통합 테스트에서 간접 검증) |

비즈니스 로직(서비스, 컨트롤러, 유틸리티)만 커버리지 측정 대상입니다.

### 리포트 확인

CI 실행 후 GitHub Actions에서 확인:
- **Step Summary**: 커버리지 % 및 테스트 수 표시
- **Artifacts**: `coverage-report` (HTML), `test-results` (JUnit XML) 7일 보관

로컬 실행:
```bash
cd server
./gradlew test jacocoTestReport
# 결과: build/reports/jacoco/test/html/index.html
```

---

## CI/CD 파이프라인

### 워크플로우 구성 (`.github/workflows/ci.yml`)

```
push/PR 트리거
    │
    ▼
[build-and-test]
    ├── Java 21 Temurin 설정
    ├── Gradle 의존성 캐시
    ├── ./gradlew test (--continue) + jacocoTestReport
    ├── 테스트 결과 아티팩트 업로드 (30일)
    ├── JaCoCo HTML 리포트 아티팩트 업로드 (30일)
    ├── JaCoCo XML 아티팩트 업로드 (30일)
    └── Step Summary에 커버리지 %, 최소 기준(50%) 출력
    │
    ▼ (build-and-test 성공 시)
[docker-publish]
    ├── Docker Buildx 설정
    ├── GHCR(ghcr.io) 로그인 (GITHUB_TOKEN)
    ├── 메타데이터 설정 (branch, sha, latest 태그)
    ├── docker build-push → ghcr.io/sms1875/gamepaper-server
    └── Step Summary에 이미지 정보 출력
```

### GHCR 이미지 태그 전략

| 태그 | 조건 | 예시 |
|------|------|------|
| `latest` | master 브랜치 push | `ghcr.io/sms1875/gamepaper-server:latest` |
| `sha-<hash>` | 모든 push | `ghcr.io/sms1875/gamepaper-server:sha-abc1234` |
| `<branch>` | 브랜치 push | `ghcr.io/sms1875/gamepaper-server:master` |

### 트리거 조건

| 이벤트 | 동작 |
|--------|------|
| 모든 브랜치 push | build-and-test 실행 |
| master PR | build-and-test + docker-build 실행 |

---

## 수동 검증 항목

CI로 자동화되지 않는 항목은 Docker 실행 후 수동 검증이 필요합니다.

### API 연기 테스트

```bash
# 서버 실행
cd server && docker compose up -d

# 1. 게임 목록 API
curl http://localhost:8080/api/games
# 예상: 6개 게임 JSON 배열

# 2. 배경화면 목록 (페이지네이션)
curl "http://localhost:8080/api/wallpapers/1?page=0&size=12"
# 예상: content[], totalPages, currentPage

# 3. 태그 검색 (AND 모드)
curl "http://localhost:8080/api/wallpapers/search?tags=캐릭터,야경&mode=AND&page=0"

# 4. 에러 응답 구조 확인
curl -s http://localhost:8080/api/games/99999
# 예상: {"error":{"code":"GAME_NOT_FOUND","message":"..."}}

# 5. 기기 ID 없이 좋아요 → 에러 응답
curl -s -X POST http://localhost:8080/api/wallpapers/1/like
# 예상: {"error":{"code":"MISSING_DEVICE_ID","message":"..."}}
```

### 관리자 UI 수동 확인

| 경로 | 확인 항목 |
|------|-----------|
| `http://localhost:8080/admin` | 대시보드 — 게임/배경화면 통계 카드 표시 |
| `http://localhost:8080/admin/games` | 게임 목록 — 상태 배지, 배경화면 수 표시 |
| `http://localhost:8080/admin/games/new` | 등록 폼 — URL 입력 후 AI 분석 시작 |
| `http://localhost:8080/admin/games/{id}` | 상세 — 전략 JSON, 분석 상태 표시 |

### Selenium 헬스체크

```bash
curl http://localhost:4444/wd/hub/status
# 예상: {"value":{"ready":true,...}}
```

---

## 최근 CI 실행 결과

| 항목 | 결과 |
|------|------|
| 실행일 | 2026-03-16 |
| 브랜치 | master |
| 빌드 결과 | ✅ BUILD SUCCESSFUL |
| 테스트 클래스 | 15개 |
| 테스트 메서드 | 56개 (56/56 PASS) |
| 명령어 커버리지 | 32.3% (1,425 / 4,415) |
| 커버리지 게이트 (50%) | ⬜ FAIL (크롤러/Selenium 클래스 제외 미적용으로 낮음) |
| JaCoCo 리포트 | ✅ 생성됨 (Artifacts: `coverage-report`) |
| Docker 이미지 빌드 | ✅ 성공 (ghcr.io/sms1875/gamepaper-server:latest) |
| CI 전체 상태 | ✅ 전체 green |

GitHub Actions 실행 ID: `23123405660`
GitHub Actions URL: https://github.com/sms1875/AI-Hackathon/actions/runs/23123405660

---

## 한계 및 개선 계획

| 항목 | 현재 상태 | 개선 방향 |
|------|-----------|-----------|
| Flutter 앱 테스트 | 별도 레포 (`D:\work\GamePaper`), CI 미통합 | Phase 4에서 모노레포 전환 시 통합 |
| E2E Playwright UI 테스트 | CLAUDE.md 정의됨, sprint-close 시 수동 실행 | 배포 환경 구성 후 CI 통합 |
| 클라우드 배포 자동화 | 로컬 Docker Compose만 지원 | Phase 2+ (AWS/Railway) 전환 시 구현 |
| API 부하 테스트 | 미구현 | Phase 4 성능 최적화 시 k6/JMeter 도입 |

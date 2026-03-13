# 데이터 흐름 문서 (Data Flow)

> 프로젝트: GamePaper
> 기반 문서: PRD.md, TECH-SPEC.md

---

## 1. 전체 시스템 데이터 흐름 개요

```
[관리자]                [Spring Boot 서버]              [외부]
   │                         │
   ├── 게임 URL 등록 ────────→│
   │                         ├── Selenium → 게임 사이트 HTML/스크린샷 수집
   │                         ├── Claude API → 파싱 전략 JSON 생성
   │                         ├── DB (SQLite) → 전략 저장
   │                         ├── GenericCrawlerExecutor → 이미지 수집
   │                         ├── LocalStorageService → 파일 저장
   │                         └── DB → 메타데이터(URL/태그/BlurHash) 저장
   │
[Flutter 앱]              [Spring Boot 서버]
   │                         │
   ├── GET /api/games ───────→├── GameRepository → DB 조회
   │←── 게임 목록 ────────────┤
   │                         │
   ├── GET /api/wallpapers ──→├── WallpaperRepository → DB 조회
   │←── 배경화면 목록+URL ────┤
   │                         │
   ├── 이미지 URL 직접 요청 ─→├── 정적 파일 서빙 (/storage/**)
   │←── 이미지 바이너리 ──────┤
```

---

## 2. 게임 등록 및 AI 분석 흐름

```
관리자 (브라우저)
    │
    ├─[1] POST /admin/games { name, url }
    │
    ↓
AdminController
    │
    ├─[2] Game 엔티티 생성, status=UPDATING → DB 저장
    ├─[3] AnalysisService.analyzeAsync(gameId) 비동기 호출
    │
    ↓ (비동기 @Async)
AnalysisService
    │
    ├─[4] CrawlerStrategy 생성, analysisStatus=PENDING → DB 저장
    ├─[5] Selenium WebDriver 초기화
    ├─[6] 대상 URL 접속 → 페이지 렌더링 대기
    ├─[7] HTML 소스 추출 + 스크린샷 캡처 (Base64)
    ├─[8] HTML 토큰 최적화 (불필요한 태그/속성 제거)
    ├─[9] analysisStatus=ANALYZING → DB 업데이트
    ├─[10] Claude API 호출 (HTML + 스크린샷)
    │      └── 응답: 파싱 전략 JSON
    ├─[11] JSON 스키마 검증
    ├─[12] CrawlerStrategy.strategyJson 저장, analysisStatus=COMPLETED
    └─[13] Game.status=ACTIVE → DB 업데이트

관리자 (브라우저) — 폴링
    │
    ├─[P] GET /admin/games/{id}/analyze/status  (2초 간격)
    │←── { status: "ANALYZING", message: "HTML 분석 중..." }
    │←── { status: "COMPLETED", strategyJson: {...} }
```

---

## 3. 크롤링 파이프라인 흐름

```
CrawlerScheduler (@Scheduled, 6시간마다)
    │
    ├─[1] GameRepository.findAllByStatus(ACTIVE) → 활성 게임 목록 조회
    │
    └─ 게임별 순회
           │
           ├─[2] CrawlerStrategyRepository.findLatestByGameId() → 최신 전략 조회
           ├─[3] Game.status = UPDATING → DB 업데이트
           ├─[4] CrawlingLog 생성 (startedAt 기록)
           │
           ↓
       GenericCrawlerExecutor.execute(game, strategy)
           │
           ├─[5] ParseStrategy JSON 파싱 (DTO 변환)
           ├─[6] requiresJavaScript → Selenium / Jsoup 선택
           ├─[7] preActions 실행 (팝업 닫기 등)
           │
           └─ 페이지 순회 루프
                  │
                  ├─[8] 이미지 요소 선택 (imageSelector)
                  ├─[9] 이미지 URL 추출 (imageAttribute)
                  ├─[10] 이미지 URL 정규식 필터링
                  ├─[11] 이미지 다운로드 (HTTP)
                  ├─[12] 중복 체크 (파일명 해시)
                  ├─[13] BlurHash 생성
                  ├─[14] 해상도(width, height) 추출
                  ├─[15] 이미지 비율 분석 → imageType 결정
                  │       (가로형: desktop, 세로형: mobile)
                  ├─[16] StorageService.upload() → 파일 저장
                  ├─[17] WallpaperRepository.save() → 메타데이터 저장
                  ├─[18] (비동기) AI 태그 생성 요청 큐에 추가
                  │
                  ├─[19] stopCondition 확인
                  ├─[20] nextPage 이동 (paginationType에 따라)
                  └─[21] maxPages 도달 시 종료
           │
           ├─[22] CrawlingLog 업데이트 (finishedAt, collectedCount, status=SUCCESS)
           └─[23] Game.status = ACTIVE, lastCrawledAt 업데이트

실패 처리:
    ├─ 예외 발생 → CrawlingLog.status=FAILED, errorMessage 저장
    ├─ 연속 3회 실패 → Game.status=FAILED
    └─ 관리자 알림 (Admin UI에서 FAILED 상태 표시)
```

---

## 4. AI 태그 생성 흐름

```
크롤링 파이프라인 (이미지 저장 완료 후)
    │
    ├─[1] 태그 생성 작업 비동기 큐 추가 (wallpaperId)
    │
    ↓ (비동기 처리)
TagGenerationService
    │
    ├─[2] WallpaperRepository.findById() → 이미지 URL 조회
    ├─[3] 이미지 파일 읽기 (StorageService)
    ├─[4] Claude Vision API 호출 (이미지 Base64)
    │      └── 응답: ["dark", "landscape", "blue-tone"]
    ├─[5] 태그 JSON 배열 → DB 저장 (Wallpaper.tags)
    └─[6] 일괄 처리 시 rate limit 준수 (요청 간 딜레이)
```

---

## 5. Flutter 앱 데이터 조회 흐름

```
앱 시작
    │
    ├─[1] 로컬 캐시 확인 (Hive) → 게임 목록 즉시 표시 (Phase 3~)
    ├─[2] GET /api/games (백그라운드 갱신)
    │      └── 응답: [{ id, name, wallpaperCount, status, lastCrawledAt }]
    └─[3] 로컬 캐시 업데이트 (TTL: 1시간)

게임 선택 → 배경화면 그리드
    │
    ├─[4] 로컬 캐시 확인 → 캐시 히트 시 즉시 표시
    ├─[5] GET /api/wallpapers/{gameId}?page=0&size=12
    │      └── 응답: { content: [...], totalPages, currentPage }
    ├─[6] BlurHash로 플레이스홀더 렌더링 (이미지 로드 전)
    ├─[7] 이미지 URL 요청 → 로컬 스토리지에서 서빙
    └─[8] 무한 스크롤 → page+1 추가 요청

배경화면 적용
    │
    ├─[9] 이미지 전체 다운로드 (캐시)
    ├─[10] WallpaperService.setHomeScreen(imagePath) 호출
    │       ├── Android: async_wallpaper 패키지
    │       ├── Windows: SystemParametersInfo Win32 API (Phase 4)
    │       ├── macOS: NSWorkspace (Phase 4)
    │       └── Linux: gsettings (Phase 4)
    └─[11] 적용 성공 토스트 표시
```

---

## 6. AI 추천 데이터 흐름

```
사용자 좋아요 액션
    │
    ├─[1] POST /api/wallpapers/{id}/like (헤더: X-Device-Id)
    ├─[2] UserLike 저장 (deviceId, wallpaperId, createdAt)
    └─[3] 토글: 이미 좋아요 → 삭제

추천 조회
    │
    ├─[1] GET /api/wallpapers/recommended (헤더: X-Device-Id)
    ├─[2] UserLike 이력 조회 → 좋아요한 wallpaperId 목록
    ├─[3] 해당 wallpaper의 tags 집계 → 선호 태그 분석
    │       예: { dark: 5, landscape: 3, blue-tone: 4 }
    ├─[4] 상위 태그로 WallpaperRepository 검색
    │       (좋아요하지 않은 항목 중 tag 매칭 점수 높은 순)
    ├─[5] (선택) Claude API 호출: 태그 조합으로 추천 로직 보강
    └─[6] 추천 목록 반환 (최대 20개)

좋아요 이력 없는 경우:
    └── 수집량 기준 인기순 (wallpapers 수 많은 게임의 최신 이미지)
```

---

## 7. 이미지 서빙 흐름

```
Flutter 앱 → 이미지 URL 요청
    │
    ├── URL 형식: http://{host}:8080/storage/images/{gameId}/{fileName}
    │
    ↓
Spring Boot ResourceHandler (/storage/**)
    │
    ├── 파일 경로 변환: /storage/images/{gameId}/{fileName}
    │                   → {STORAGE_ROOT}/images/{gameId}/{fileName}
    ├── 파일 존재 확인
    ├── Content-Type 자동 감지 (jpg/png/webp)
    └── 파일 바이너리 응답 (영구 URL, 만료 없음)
```

---

## 8. 데이터 생명주기

```
[게임 등록]
  Game 생성 (UPDATING)
      ↓
[AI 분석]
  CrawlerStrategy 생성 (PENDING → ANALYZING → COMPLETED)
  Game 업데이트 (ACTIVE)
      ↓
[크롤링 실행]
  CrawlingLog 생성
  Wallpaper 생성 (파일 + DB)
  CrawlingLog 완료 (SUCCESS)
  Game.lastCrawledAt 업데이트
      ↓
[태그 생성]
  Wallpaper.tags 업데이트
      ↓
[사용자 상호작용]
  UserLike 생성/삭제
      ↓
[게임 삭제]
  DELETE /admin/games/{id}
  → Wallpaper 목록 조회
  → StorageService.delete() 파일 삭제
  → Wallpaper, CrawlingLog, CrawlerStrategy, UserLike DB 삭제
  → Game DB 삭제
```

---

## 9. 오류 발생 시 데이터 흐름

```
[AI 분석 실패]
  CrawlerStrategy.analysisStatus = FAILED
  Game.status = ACTIVE 유지 (기존 전략으로 크롤링 계속)
  → 관리자 UI에서 FAILED 표시 + 재분석 버튼 활성화

[크롤링 실패]
  CrawlingLog.status = FAILED, errorMessage 저장
  연속 3회 실패 카운트 증가
  3회 도달 → Game.status = FAILED
  → 관리자 UI에서 FAILED 뱃지 표시
  → 재분석 버튼으로 AI 재분석 트리거 가능

[Claude API rate limit]
  Exponential backoff: 1초 → 2초 → 4초 재시도 (최대 3회)
  3회 모두 실패 → 분석 작업 FAILED 처리

[이미지 다운로드 실패]
  해당 이미지 건너뜀 (skip)
  CrawlingLog에 skip_count 기록
  크롤링 계속 진행
```

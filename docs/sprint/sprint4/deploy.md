# Sprint 4 배포 가이드

## 자동 검증 완료 항목
- ✅ Gradle compileJava + compileTestJava 빌드 성공
- ✅ AdminCrawlApiController — GenericCrawlerExecutor 연결 (Task 6)
- ✅ CrawlerScheduler — runGenericAsync, runGeneric, handleCrawlFailure 구현 (Task 6)
- ✅ CrawlingLogRepository.findTop3ByGameIdOrderByStartedAtDesc 쿼리 추가 (Task 6)
- ✅ DataInitializer — 앱 시작 시 6개 게임 초기 데이터 등록 (Task 7)
- ✅ CrawlerScheduler.runAll() — 전략 있는 게임 GenericCrawlerExecutor 우선 실행 (Task 8)
- ✅ 기존 크롤러 6개 제거 및 AdminCrawlApiController fallback 정리 (Task 10)

## 수동 검증 필요 항목
- ⬜ docker compose up --build — 새 코드 반영 재빌드
- ⬜ 관리자 UI http://localhost:8080/admin 접속 → 게임 목록에 6개 게임 표시 확인
- ⬜ 게임 등록 화면 /admin/games/new → "AI 분석 시작" 버튼 → polling 진행 상태 확인
- ⬜ 게임 상세 화면 → "재분석" 버튼 → 새 버전 전략 생성 확인
- ⬜ 크롤링 트리거 → 로그 탭에서 수집 결과 확인
- ⬜ POST /admin/games/{id}/analyze → 202 반환 확인
- ⬜ GET /admin/games/{id}/analyze/status → 상태 JSON 반환 확인
- ⬜ POST /admin/api/games/{id}/crawl → 200 반환 확인 (전략 있는 경우 GenericCrawlerExecutor 사용)
- ⬜ ANTHROPIC_API_KEY 환경변수 설정 확인 (.env 파일)

## 환경 변수 (필수)
- ANTHROPIC_API_KEY: Claude API 키 (미설정 시 데모 전략 사용)
- selenium.hub-url: Selenium Hub URL (기본: http://localhost:4444)
- crawler.schedule.delay-ms: 크롤링 주기 ms (기본: 21600000 = 6시간)

## 주요 변경 사항 요약

### Task 6: AdminCrawlApiController + CrawlerScheduler
- AdminCrawlApiController에 GenericCrawlerExecutor 의존성 추가
- 크롤링 트리거 우선순위: CrawlerStrategy 있으면 GenericCrawlerExecutor, 없으면 기존 크롤러
- CrawlerScheduler에 `runGenericAsync()`, `runGeneric()` 메서드 추가
- 연속 3회 실패 시 게임 상태 FAILED 전환 (handleCrawlFailure)
- CrawlingLogRepository에 `findTop3ByGameIdOrderByStartedAtDesc` 추가

### Task 7: DataInitializer
- 앱 시작 시 6개 게임 초기 데이터 자동 등록 (CommandLineRunner)
- URL 중복 체크로 멱등성 보장 (GameRepository.existsByUrl)
- 등록 게임: 원신, 마비노기, 메이플스토리 모바일, NIKKE, 파이널판타지 XIV, 검은사막

### Task 8: CrawlerScheduler 전략 우선 실행
- runAll() 메서드: 전략 있는 게임 → GenericCrawlerExecutor, 없는 게임 → 기존 크롤러
- INACTIVE 상태 게임 스케줄 실행 제외

### Task 10: 기존 크롤러 제거
- selenium/: GenshinCrawler, MabinogiCrawler, MapleStoryCrawler, NikkeCrawler 제거
- jsoup/: FinalFantasyXIVCrawler, BlackDesertCrawler 제거
- AdminCrawlApiController: GameCrawler 목록 의존성 및 fallback 제거
- CrawlerScheduler: GameCrawler 목록 의존성 및 fallback 제거

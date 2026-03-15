# Sprint 4 코드 리뷰

**리뷰 일자:** 2026-03-15
**리뷰 대상:** sprint4 브랜치 전체 변경 사항 (master 대비)
**변경 규모:** 30개 파일, +3393/-662 줄

---

## 요약

| 구분 | 건수 |
|------|------|
| Critical | 0 |
| Important | 3 |
| Suggestion | 4 |

Sprint 4의 핵심 목표인 비동기 AI 분석 파이프라인과 GenericCrawlerExecutor는 구조적으로 잘 설계되어 있습니다. Sprint 3 코드 리뷰 이슈 3건 모두 해소되었습니다.

---

## Important (중요 - 추후 개선 권고)

### I-1: AnalysisService — 게임 조회 후 상태 변경 시 트랜잭션 경계 부재

**파일:** `server/src/main/java/com/gamepaper/claude/AnalysisService.java`

`analyzeAsync()`는 `@Async`로 실행되는데, 게임 상태를 여러 단계에서 별도 `save()` 호출로 변경합니다. 각 `save()`는 별도 트랜잭션이므로 중간에 예외 발생 시 상태가 일부만 변경될 수 있습니다.

```java
// 현재: 각 save()가 별도 트랜잭션
game.setAnalysisStatus(AnalysisStatus.ANALYZING);
gameRepository.save(game);  // 트랜잭션 1
// ... 분석 로직 ...
game.setAnalysisStatus(AnalysisStatus.COMPLETED);
gameRepository.save(game);  // 트랜잭션 2
```

**개선 방향:** 각 상태 변경 메서드에 `@Transactional` 추가 또는 상태 변경 전용 repository 메서드(`updateAnalysisStatus(id, status)`) 도입.

---

### I-2: GenericCrawlerExecutor — ObjectMapper 정적 필드 사용

**파일:** `server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java`

```java
private static final ObjectMapper MAPPER = new ObjectMapper();
```

`ObjectMapper`는 스레드 안전하지만 Spring 빈으로 주입받는 것이 설정 일관성(예: JavaTimeModule 등록 여부)과 테스트 용이성 측면에서 바람직합니다.

**개선 방향:** `@Autowired`로 주입받거나 `AppConfig`에 `@Bean ObjectMapper` 등록 후 재사용.

---

### I-3: AdminAnalyzeApiController — 기존 엔드포인트(`/admin/api/analyze`) 하위 호환 유지

**파일:** `server/src/main/java/com/gamepaper/admin/AdminAnalyzeApiController.java`

기존 `/admin/api/analyze` 엔드포인트가 "하위 호환"으로 남아 있으나, 현재 프론트엔드 `game-new.html`이 새 엔드포인트로 전환되었다면 레거시 엔드포인트는 제거 대상입니다. 불필요한 엔드포인트가 늘어나면 API 표면이 증가합니다.

**개선 방향:** Sprint 5 이후 `/admin/api/analyze` 사용처가 없으면 제거 검토. 하위 호환이 필요한 경우 `@Deprecated` 애노테이션 명시.

---

## Suggestion (개선 제안)

### S-1: DataInitializer — 초기 데이터 URL 변경 시 갱신 로직 부재

**파일:** `server/src/main/java/com/gamepaper/config/DataInitializer.java`

현재 `existsByUrl`로 멱등성을 보장하지만, URL이 변경된 게임 데이터를 재등록하려면 수동 삭제가 필요합니다.

**제안:** 게임명으로도 중복 체크하거나, 개발 환경에서는 `UPSERT` 방식 적용.

---

### S-2: CrawlerScheduler.runAll() — 스케줄링 중 동기 실행으로 블로킹 가능

**파일:** `server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`

`@Scheduled` 콜백 내에서 `runGeneric()`을 동기적으로 반복 호출합니다. 게임 수가 많아지면 스케줄러 스레드가 장기 블로킹될 수 있습니다.

**제안:** 루프 내에서 `runGenericAsync()` 호출로 변경하여 각 게임 크롤링을 병렬 실행.

---

### S-3: GenericCrawlerExecutor — scroll 페이지네이션 종료 조건이 maxPages에만 의존

**파일:** `server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java`

`scroll` 타입 페이지네이션은 `maxPages`에만 의존하므로, 실제로 페이지 끝에 도달했어도 `maxPages`만큼 스크롤을 반복합니다.

**제안:** 스크롤 전후 이미지 수가 동일하면 중단하는 `stopCondition` 로직 추가.

---

### S-4: StrategyDto — `preActions` 타입이 `List<Map<String, String>>`으로 느슨함

**파일:** `server/src/main/java/com/gamepaper/crawler/generic/StrategyDto.java`

`preActions`가 제네릭 `Map`으로 처리되어 타입 안전성이 낮습니다.

**제안:** `PreAction` 전용 DTO 클래스(`type`, `selector`, `value` 필드) 도입.

---

## 긍정 사항

- Sprint 3 코드 리뷰 이슈 3건 모두 해소:
  - I-1: `new Thread()` → `@Async("asyncExecutor")` 교체 완료
  - I-2: `RestClient.create()` → `RestClient.Builder` 빈 재사용 완료
  - I-3: `GameStatus.INACTIVE` 추가 완료
- `AnalysisService` 상태 전이 흐름(`PENDING → ANALYZING → COMPLETED | FAILED`)이 명확하게 문서화됨
- `GenericCrawlerExecutor`의 `finally` 블록에서 드라이버 세션 반드시 종료 — 메모리 누수 방지 패턴 올바름
- `DataInitializer`의 `existsByUrl` 중복 체크로 멱등성 보장
- 기존 6개 게임별 크롤러 제거로 코드베이스 단순화 (`-662` 줄)

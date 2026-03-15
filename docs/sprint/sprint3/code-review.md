# Sprint 3 코드 리뷰

**검토 대상:** sprint3 브랜치 (master 대비 변경 사항)
**검토 일시:** 2026-03-15
**검토 범위:** 28개 파일, +1,544줄

---

## 요약

| 구분 | 건수 |
|------|------|
| Critical | 0 |
| Important | 3 |
| Suggestion | 4 |

전체적으로 구조가 명확하고 관심사 분리가 잘 되어 있습니다. 테스트 22개 전부 통과하였으며 주요 로직에 예외 처리가 적절히 구현되어 있습니다.

---

## Critical (즉시 수정 필요)

없음.

---

## Important (다음 스프린트 전 수정 권장)

### I-1. `AdminCrawlApiController` — 원시 Thread 사용 (비관리 스레드)

**위치:** `server/src/main/java/com/gamepaper/admin/AdminCrawlApiController.java:44`

```java
new Thread(() -> crawlerScheduler.runSingle(target)).start();
```

**문제:** `new Thread()`로 직접 생성한 스레드는 Spring의 예외 처리, 트랜잭션, 로깅 컨텍스트에서 완전히 벗어납니다. 크롤링 실패 시 에러가 소실될 수 있습니다.

**권장 조치:** Sprint 4에서 `@Async` + `TaskExecutor` 빈 또는 `CompletableFuture`로 교체. 현재는 주석에 Sprint 4 예정임이 명시되어 있어 계획된 기술 부채임을 확인.

---

### I-2. `ClaudeApiClient` — RestClient 매번 생성

**위치:** `server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java:108`

```java
String responseBody = RestClient.create()
        .post()
        ...
```

**문제:** `RestClient.create()`를 매 요청마다 호출하면 내부 빌더가 매번 초기화됩니다. Spring Boot의 `RestClient.Builder` 빈을 주입받아 재사용하는 패턴이 권장됩니다.

**권장 조치:** `@Autowired RestClient.Builder restClientBuilder`를 주입받고 `@PostConstruct`에서 한 번만 빌드하여 필드에 저장.

---

### I-3. `AdminGameController` — `toggleStatus`에서 비활성화를 `FAILED`로 처리

**위치:** `server/src/main/java/com/gamepaper/admin/AdminGameController.java:68`

```java
game.setStatus(com.gamepaper.domain.game.GameStatus.FAILED); // 비활성화는 FAILED로 표시
```

**문제:** `FAILED`는 크롤링 실패 의미이므로 관리자가 의도적으로 비활성화한 게임과 의미 혼동이 생깁니다. 추후 Sprint 4에서 실패 재분석 로직 구현 시 오동작 가능.

**권장 조치:** `GameStatus`에 `INACTIVE` 항목 추가를 검토하거나, 비활성화 상태를 Game 엔티티의 별도 boolean 필드(`active`)로 관리.

---

## Suggestion (개선 제안)

### S-1. `HtmlFetcher` — URL 유효성 검증 없음

`fetch(url)` 호출 전에 URL 형식 검증(`url.startsWith("http")` 등)을 추가하면 Jsoup 예외 메시지가 더 명확해집니다.

---

### S-2. `CrawlerStrategyParser` — 코드 블록 없는 JSON 처리 불안정

`extractJson`에서 코드 블록이 없을 경우 전체 텍스트를 JSON으로 파싱 시도합니다. Claude 응답에 설명 텍스트가 포함되면 파싱 실패합니다. 중괄호 범위로 JSON을 추출하는 fallback 로직(`text.indexOf('{')` ~ `text.lastIndexOf('}')`) 추가 권장.

---

### S-3. `AdminAnalyzeApiController` — 분석 결과를 응답 구조 불일치

`strategy` 키로 `Map<String, Object>`를 반환하지만, 데모 전략과 실제 전략의 반환 타입이 각각 `Map<String, Object>`와 `JsonNode`로 다릅니다. 공통 응답 DTO 클래스 도입 권장.

---

### S-4. Thymeleaf 템플릿 — XSS 방어 확인 필요

`game-detail.html` 등에서 `th:utext` 대신 `th:text`가 사용되고 있어 기본적인 XSS 방어는 동작합니다. `strategyJson`처럼 JSON 문자열을 `<textarea>`에 넣는 경우 `th:text`를 유지해야 합니다. 현재 코드는 안전하나 향후 JSON 에디터 도입 시 재확인 필요.

---

## Sprint 1/2 이슈 후속 확인

| 이슈 | 상태 |
|------|------|
| I-1 (Sprint 1): StorageService 예외 처리 보강 | `listFiles()`에서 IOException catch 후 빈 목록 반환으로 처리됨 — 해소 |
| I-2 (Sprint 1): @Value 기본값 처리 | `ClaudeApiClient`에서 `@Value("${claude.api-key:}")`로 기본값 처리 적용 — 동일 패턴 준수 |
| I-3 (Sprint 2): CrawlingLogStatus enum 통일 | Sprint 2에서 이미 해소됨 |

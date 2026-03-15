# Sprint 2 코드 리뷰 보고서

**리뷰 일자**: 2026-03-15
**리뷰 대상**: sprint2 브랜치
**리뷰어**: code-reviewer agent

---

## 전체 평가

Sprint 2 구현이 계획 문서(`docs/sprint/sprint2.md`)에 명시된 요구사항을 충실하게 이행했습니다. Sprint 1 I-3 이슈(CrawlingLog.status 타입 불일치)가 Task 0에서 해소되었고, 크롤러 인터페이스 설계가 Sprint 4 GenericCrawlerExecutor 교체를 고려한 확장 구조로 구현되었습니다. 예외 처리와 방어적 코딩이 전반적으로 잘 적용되어 있습니다.

---

## Critical 이슈 (없음)

Critical 이슈가 발견되지 않았습니다.

---

## Important 이슈

### I-1. `GenshinCrawler` — CSS 선택자 의존성이 높아 사이트 변경에 취약

**파일**: `server/src/main/java/com/gamepaper/crawler/selenium/GenshinCrawler.java`

```java
List<WebElement> images = driver.findElements(
    By.cssSelector("img[src*='hoyolab'][src*='.jpg'], img[src*='hoyolab'][src*='.png']"));
```

도메인 이름(`hoyolab`)을 CSS 선택자에 하드코딩하여 사이트 URL 변경 또는 CDN 전환 시 즉시 수집이 중단됩니다. 이는 ROADMAP이 이미 인지한 리스크 항목("크롤링 대상 사이트 구조 변경 — 높음")이지만, 현재 구현에서는 실패 감지가 `count == 0` 이거나 예외 발생 시에만 가능합니다. 수집 결과가 0건이어도 `CrawlResult.success(0)`을 반환하므로 조용한 실패가 될 수 있습니다.

**권고**: 수집 건수가 0이면 경고 로그를 남기고, 일정 횟수 반복 시 `FAILED` 상태로 전환하는 로직을 Sprint 3~4에서 `CrawlerScheduler`에 추가합니다. (현재는 현 상태 유지 허용)

---

### I-2. `AbstractGameCrawler` — `HttpClient` 인스턴스가 인스턴스 필드로 생성

**파일**: `server/src/main/java/com/gamepaper/crawler/AbstractGameCrawler.java`

```java
private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();
```

크롤러 인스턴스마다 `HttpClient`가 별도 생성됩니다. 6개 크롤러가 동시에 동작할 때 스레드 풀을 공유하지 않아 리소스 낭비가 발생합니다. `HttpClient`는 스레드 안전하며 재사용이 권장됩니다.

**권고**: `HttpClient`를 Spring Bean으로 선언하여 싱글톤으로 주입하거나, `static final`로 선언합니다. Sprint 3~4 리팩토링 시 개선을 고려합니다.

---

### I-3. `FinalFantasyXIVCrawler`, `BlackDesertCrawler` — gameId를 `@Value`로 주입

**파일**: `server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java`

```java
@Value("${game.id.ffxiv:5}")
private Long gameId;
```

gameId를 환경변수로 주입받도록 설계되었는데, 이는 설정과 코드 사이의 암묵적 결합을 만듭니다. `data-local.sql`에 고정 ID(1~6)가 지정되어 있고 크롤러 코드에도 기본값이 하드코딩되어 있어, 두 값이 불일치하면 크롤러가 존재하지 않는 게임 ID로 실행됩니다.

**권고**: Sprint 4 GenericCrawlerExecutor 전환 시 이 문제가 자연스럽게 해소되므로 현재는 현 상태 유지. 단, `data-local.sql`과 `@Value` 기본값이 동기화되어 있는지 확인이 필요합니다. (현재 확인 결과: 동기화 상태 양호)

---

## Suggestion (권고 사항)

### S-1. `CrawlerScheduler` — 연속 실패 감지 로직 없음

현재 크롤러 실패 시 `CrawlingLogStatus.FAILED`로 기록되지만, ROADMAP에 명시된 "크롤링 연속 3회 실패 시 `CrawlerStatus.FAILED` 자동 전환" 로직이 없습니다. Sprint 4 DoD 항목이므로 현재는 정상이나, Sprint 3~4 구현 전 메모로 남깁니다.

### S-2. `AbstractSeleniumCrawler` — WebDriver 생성 실패 시 예외 타입이 `RuntimeException`

```java
throw new RuntimeException("Selenium 드라이버 생성 실패: " + seleniumHubUrl, e);
```

범용 `RuntimeException` 대신 `CrawlerException`과 같은 도메인 예외 클래스를 사용하면 추후 예외 처리 및 모니터링이 용이해집니다. Sprint 6 구조화 단계에서 개선 고려.

### S-3. `ImageProcessor` — `BlurHash` 라이브러리 의존성 품질 확인 필요

`io.trbl.blurhash` 라이브러리가 활성 유지보수 중인지 확인이 필요합니다. 대안으로 `io.github.pulseenergy:blurhash` 또는 직접 구현을 Sprint 계획 문서에서 언급한 `io.github.pulseenergy:blurhash`와 실제 사용 라이브러리가 다를 수 있습니다.

---

## 계획 대비 구현 검토

| 계획 항목 | 구현 여부 | 비고 |
|-----------|-----------|------|
| Task 0: CrawlingLog.status enum 수정 | 완료 | Sprint 1 I-3 이슈 해소 |
| GameCrawler 인터페이스 | 완료 | Sprint 4 재활용 구조 주석 포함 |
| CrawlerScheduler + 6시간 주기 스케줄 | 완료 | 환경변수 오버라이드 지원 |
| CrawlResult (성공/실패/건수) | 완료 | |
| ImageProcessor (BlurHash + 해상도) | 완료 | 실패 시 기본값 반환 (안전) |
| AbstractGameCrawler (공통 이미지 저장 로직) | 완료 | URL 해시 기반 중복 체크 |
| Jsoup 크롤러 2개 (FFXIV, 검은사막) | 완료 | 2초 딜레이 |
| AbstractSeleniumCrawler | 완료 | 세션 종료 (`quitDriver`) 구현 |
| Selenium 크롤러 4개 (원신, 마비노기, 메이플, NIKKE) | 완료 | |
| Docker Compose Selenium 서비스 | 완료 | shm_size 2g, 헬스체크 |
| 게임 초기 데이터 6개 | 완료 | INSERT OR IGNORE |
| Flutter Firebase → REST API 전환 | 완료 | 별도 레포 커밋 25aaf87 |
| GitHub Actions CI (.github/workflows/ci.yml) | 완료 | Java 21, Gradle 캐시 |
| ImageProcessorTest (5개) | 완료 | 단위 테스트 |

**계획 대비 추가 구현**: `WallpaperRepository`에 `existsByGameIdAndFileName()` 메서드를 통한 URL 해시 기반 중복 체크가 `AbstractGameCrawler`에 구현되었습니다. 계획에는 없었으나 재실행 시 중복 저장 방지를 위한 중요한 개선입니다.

---

## 결론

Sprint 2 구현은 계획을 충실히 이행했습니다. Critical 이슈는 없으며, Important 이슈 3개는 모두 Sprint 4 GenericCrawlerExecutor 전환 또는 Sprint 6 리팩토링에서 자연스럽게 해소될 수준입니다. Phase 1 마일스톤(M1) 달성을 위한 코드 품질이 확보되었으며 현재 코드 상태로 Sprint 3 진행이 가능합니다.

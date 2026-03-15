# Sprint 1 코드 리뷰 보고서

**리뷰 일자**: 2026-03-13
**리뷰 대상**: sprint1 브랜치 (PR #1: feat: Sprint 1 완료 - 백엔드 인프라 추상화)
**리뷰어**: code-reviewer agent

---

## 전체 평가

Sprint 1 구현이 계획 문서(`docs/sprint/sprint1.md`)에 명시된 요구사항을 충실하게 이행했습니다. 8개 테스트가 모두 통과하였고, 코드 구조가 계획된 패키지 레이아웃을 준수합니다.

---

## Critical 이슈 (없음)

Critical 이슈가 발견되지 않았습니다.

---

## Important 이슈

### I-1. `GameApiController` — 게임별 wallpaperCount N+1 쿼리 위험

**파일**: `server/src/main/java/com/gamepaper/api/GameApiController.java`

```java
return games.stream()
    .map(game -> new GameDto(game, wallpaperRepository.countByGameId(game.getId())))
    .collect(Collectors.toList());
```

게임 목록을 조회한 후 게임 수만큼 `countByGameId` 쿼리가 추가 발생합니다. Sprint 1 단계에서는 데이터가 없어 문제가 없으나, Sprint 2에서 크롤러가 데이터를 채우기 시작하면 성능 저하가 발생할 수 있습니다.

**권고**: Sprint 3 이전에 `@Query`로 JOIN COUNT 쿼리를 단일 호출로 합치는 것을 고려합니다.

---

### I-2. `StorageConfig` — `WebConfig`와 `WebMvcConfigurer` 이중 구현

**파일**: `server/src/main/java/com/gamepaper/config/StorageConfig.java`, `WebConfig.java`

두 클래스가 각각 `WebMvcConfigurer`를 구현하고 있습니다. Spring이 두 구성을 모두 적용하므로 현재는 동작에 문제가 없지만, 향후 `addCorsMappings` 또는 `addResourceHandlers`를 동시에 수정할 경우 혼선이 생길 수 있습니다.

**권고**: 두 구성을 하나의 `WebMvcConfig` 클래스로 통합하는 것을 Sprint 3 리팩토링 시 고려합니다. Sprint 1에서는 현 상태 유지 허용.

---

### I-3. `CrawlingLog.status` 필드 타입 불일치

**파일**: `server/src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java`

`CrawlingLog.status`가 `String` 타입(`@Column(length = 20)`)으로 선언되었으나, `CrawlingLogStatus` enum이 별도 파일로 이미 존재합니다. 타입 불일치가 발생하여 향후 enum 값이 코드에서 일관성 없이 사용될 수 있습니다.

**권고**: `String status` → `@Enumerated(EnumType.STRING) CrawlingLogStatus status`로 변경합니다.

---

## Suggestion (권고 사항)

### S-1. `WallpaperApiController` — 페이지 크기 상한 없음

`size` 파라미터에 상한이 없어 클라이언트가 임의로 큰 값(예: `size=10000`)을 넘길 수 있습니다. Sprint 3 API 안정화 시 `@Max(100)` 또는 서비스 계층에서 상한 처리를 추가합니다.

### S-2. `DatabaseConfig` — 테스트 시 `PRAGMA journal_mode=WAL` 실행

`@SpringBootTest` 기반 통합 테스트(`GameApiControllerTest`)가 인메모리 SQLite를 사용하는데 `DatabaseConfig`의 `@PostConstruct`가 실행되면서 WAL PRAGMA가 인메모리 DB에도 적용됩니다. 인메모리 SQLite는 WAL을 지원하지 않아 예외는 발생하지 않지만 PRAGMA가 무시됩니다. 현재 동작에는 문제가 없으나, 명시적으로 `@Profile("local")`을 `DatabaseConfig`에 추가하는 것을 검토합니다.

### S-3. `LocalStorageService` — 파일명 경로 순회 취약점 (낮은 위험)

현재 단계에서 클라이언트 직접 호출은 없으나, 향후 API로 파일명을 받을 때 `fileName`에 `../`가 포함될 경우 경로를 벗어날 수 있습니다. Sprint 2에서 크롤러가 파일명을 생성할 때 UUID 또는 slug 기반으로 고정하면 자연스럽게 해결됩니다.

---

## 계획 대비 구현 검토

| 계획 항목 | 구현 여부 | 비고 |
|-----------|-----------|------|
| Spring Boot 3.x + Java 21 | 완료 | build.gradle 확인 |
| `application.yml` local/prod 프로파일 | 완료 | `application-local.yml` 분리 |
| StorageService 인터페이스 | 완료 | `listFiles()` Sprint 3 이동 계획 준수 |
| LocalStorageService `@Profile("local")` | 완료 | |
| `/storage/**` 정적 서빙 | 완료 | StorageConfig |
| Game/Wallpaper/CrawlingLog 엔티티 | 완료 | |
| SQLite WAL 모드 활성화 | 완료 | DatabaseConfig `@PostConstruct` |
| `GET /api/games` | 완료 | |
| `GET /api/wallpapers/{gameId}` | 완료 | 계획 대비 404 처리 추가됨 (긍정적 개선) |
| DTO (GameDto, WallpaperDto, PagedResponse) | 완료 | |
| 멀티스테이지 Dockerfile | 완료 | |
| docker-compose.yml 볼륨 마운트 | 완료 | |
| 테스트 8개 | 완료 | BUILD SUCCESSFUL |

**계획 대비 추가 구현**: `WallpaperApiController`에 `gameRepository.existsById()` 검증 및 404 예외 처리가 추가되었습니다. 계획에는 없었으나 API 품질을 향상시키는 긍정적인 개선입니다.

---

## 결론

Sprint 1 구현은 계획을 충실히 이행했습니다. Critical 이슈는 없으며, Important 이슈 3개는 모두 Sprint 2~3에서 자연스럽게 해소되는 수준입니다. 현재 코드 상태로 Sprint 2 진행이 가능합니다.

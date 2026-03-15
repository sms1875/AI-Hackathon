# Sprint 5 코드 리뷰

**리뷰 대상:** sprint5 브랜치 (sprint4..sprint5 diff)
**리뷰 일시:** 2026-03-15
**리뷰 범위:** TaggingService, BatchTaggingService, WallpaperSearchService, RecommendationService, UserLike, WallpaperApiController(검색/좋아요/추천 엔드포인트), TagApiController, Flutter 모델/레포지토리/UI 위젯

---

## 요약

| 등급 | 건수 |
|------|------|
| Critical | 0 |
| Important | 3 |
| Suggestion | 4 |

---

## Critical (즉시 수정 필요)

없음.

---

## Important (다음 스프린트 수정 권장)

### I-1. WallpaperSearchService — ObjectMapper 인스턴스 매번 생성

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`

**문제:** `parseTagsFromJson()` 메서드 내부에서 `new ObjectMapper()`를 매 호출마다 생성한다. ObjectMapper는 생성 비용이 크며 스레드 안전하므로 싱글턴으로 공유해야 한다.

```java
// 현재 코드 (문제)
com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

// 권장 코드
private static final ObjectMapper MAPPER = new ObjectMapper();
```

**영향:** 검색 요청이 많아질 경우 GC 압박과 성능 저하로 이어질 수 있다.

---

### I-2. WallpaperSearchService — 인메모리 전체 스캔 확장성 문제

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`

**문제:** `findAllTagged()`로 태그가 있는 모든 배경화면을 메모리에 로드한 뒤 스트림 필터링한다. 배경화면 수가 수만 건으로 증가하면 OOM 및 응답 지연이 발생할 수 있다.

**권장:** 단기적으로는 `MAX_RESULTS` 제한을 Pageable로 DB 레벨에서 처리하거나, SQLite JSON 함수(`json_each`)를 활용한 쿼리로 전환을 검토한다. Sprint 6 리팩토링 시 개선 대상으로 등록한다.

---

### I-3. WallpaperApiController — `/{gameId}` 경로와 `/search` 경로 충돌 가능성

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperApiController.java`

**문제:** `GET /api/wallpapers/{gameId}`와 `GET /api/wallpapers/search`가 동일 컨트롤러에 존재한다. Spring MVC는 리터럴 경로를 변수 경로보다 우선 매핑하므로 현재는 동작하지만, 향후 `/recommended`, `/search` 등이 추가될 경우 명시적 순서 의존성이 높아진다.

**권장:** `/api/wallpapers/search`와 `/api/wallpapers/recommended`를 별도 컨트롤러(`WallpaperSearchApiController`)로 분리하거나, 경로를 `/api/search/wallpapers`로 변경하여 의미를 명확히 한다.

---

## Suggestion (개선 제안)

### S-1. TaggingService — `toJsonString()` 위치 재고

`toJsonString()`은 태그 직렬화 책임을 가지는데, 이는 도메인 변환 로직이므로 `TaggingService`보다 `WallpaperDto` 또는 별도 `TagJsonConverter`에 두는 것이 단일 책임 원칙에 맞다.

---

### S-2. RecommendationService — 좋아요 없을 때 빈 목록 반환 정책 명시 필요

`recommend(deviceId)` 가 좋아요 이력 없거나 태그 없는 경우 빈 목록을 반환한다. 현재는 Flutter 앱 `RecommendedSection`이 빈 목록일 때 섹션을 숨기는 방식이나, API 응답 명세에 이 정책을 명시적으로 문서화하면 클라이언트 개발자에게 유용하다.

---

### S-3. UserLike — `@Transactional` 미적용 (toggleLike)

`WallpaperApiController.toggleLike()`에서 `existsByDeviceIdAndWallpaperId` → `deleteByDeviceIdAndWallpaperId` → `countByWallpaperId` 를 순차 실행한다. 동시 요청 시 race condition이 발생할 수 있으므로 서비스 레이어로 분리하고 `@Transactional`을 적용하는 것을 권장한다.

---

### S-4. BatchTaggingService — rate limit 대기 로직 미구현

배치 태깅 시 Claude Vision API를 연속 호출하는데, 현재 호출 간 딜레이가 없다. API rate limit(분당 호출 수) 초과 시 일부 이미지에 태그가 생성되지 않는다. `Thread.sleep(200)` 수준의 최소 딜레이 또는 exponential backoff 재시도 로직 추가를 권장한다.

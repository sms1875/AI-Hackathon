# Sprint 6 코드 리뷰 보고서

**리뷰 일자:** 2026-03-15
**브랜치:** sprint6
**변경 파일 수:** 15개 (서버 12개, docs 3개)
**테스트 결과:** 56개 전체 PASS (BUILD SUCCESSFUL)

---

## 요약

| 분류 | 건수 |
|------|------|
| Critical | 0 |
| Important | 2 |
| Suggestion | 3 |

Sprint 5 코드 리뷰에서 제기된 3개 Important 이슈(I-1~I-3)가 모두 올바르게 해소되었습니다. 에러 응답 표준화 및 테스트 보강도 잘 구현되었습니다.

---

## Critical 이슈 (없음)

---

## Important 이슈

### I-1. WallpaperSearchService — findAllTagged 페이지 처리가 단일 페이지에 고정됨

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`

**문제:**
```java
Pageable pageable = PageRequest.of(0, SCAN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
List<Wallpaper> page = wallpaperRepository.findAllTagged(pageable).getContent();
```
SCAN_PAGE_SIZE(500건)를 초과하는 데이터가 있을 경우 501번째 이후 데이터가 검색에서 누락됩니다. 현재는 `PageRequest.of(0, ...)` — 즉 첫 번째 페이지만 조회하는 단일 페이지 방식입니다. 데이터가 500건을 초과할 경우 AND/OR 검색, 태그 빈도 분석 모두 불완전한 결과를 반환하게 됩니다.

**권장 수정 방향:**
- 단기: SCAN_PAGE_SIZE를 충분히 크게 설정하고 주석으로 한계를 명시
- 중기: `Slice` 기반 스트리밍 또는 커서 기반 페이지 반복 처리로 전환

---

### I-2. GlobalExceptionHandler — 문자열 매칭 기반 ErrorCode 해석의 취약성

**파일:** `server/src/main/java/com/gamepaper/api/error/GlobalExceptionHandler.java`

**문제:**
```java
private ErrorCode resolveErrorCode(ResponseStatusException ex) {
    String reason = ex.getReason() != null ? ex.getReason() : "";
    if (reason.contains("게임을 찾을 수 없습니다")) return ErrorCode.GAME_NOT_FOUND;
    if (reason.contains("배경화면을 찾을 수 없습니다")) return ErrorCode.WALLPAPER_NOT_FOUND;
    ...
}
```
에러 메시지 문자열에 의존하는 방식은 메시지 변경 시 ErrorCode 매핑이 깨집니다. `ResponseStatusException`의 서브클래스 또는 커스텀 예외 클래스를 사용하면 타입 안전하게 처리할 수 있습니다.

**권장 수정 방향:**
- `GameNotFoundException extends ResponseStatusException`처럼 커스텀 예외 클래스 정의
- 각 컨트롤러에서 커스텀 예외를 throw하도록 변경
- GlobalExceptionHandler에서 타입으로 분기 처리

---

## Suggestion

### S-1. WallpaperLikeApiController — 좋아요 응답에 타입 안전한 DTO 사용 권장

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperLikeApiController.java`

```java
return Map.of("liked", false, "likeCount", likeCount);
```
`Map<String, Object>` 반환 대신 `LikeResponse` 레코드를 정의하면 API 문서 자동 생성, 직렬화 안정성, 테스트 가독성이 향상됩니다.

---

### S-2. WallpaperSearchService — searchByTagsAnd/Or 코드 중복

**파일:** `server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`

`searchByTagsAnd`와 `searchByTagsOr`에서 `findAllTagged(pageable)` 호출 + stream 처리가 중복됩니다. `findTaggedPage()` 헬퍼 메서드로 추출하면 중복을 제거할 수 있습니다.

---

### S-3. Flutter LocalCache — 캐시 키 충돌 가능성

**파일:** `client/lib/cache/local_cache.dart` (Flutter 레포)

게임 ID나 페이지 번호가 캐시 키에 포함되지 않을 경우 서로 다른 데이터가 같은 키를 공유할 수 있습니다. 캐시 키 설계를 문서화하거나 상수로 관리하는 것을 권장합니다.

---

## 긍정적 변경사항

- ObjectMapper static 상수화(I-1) — 스레드 안전성 확인: ObjectMapper 자체는 스레드 안전하므로 올바른 패턴입니다.
- 컨트롤러 분리(I-3) — WallpaperApiController, WallpaperLikeApiController, WallpaperRecommendApiController로 명확하게 분리되었습니다.
- ErrorResponse record 타입 사용 — 불변성과 간결성이 확보되었습니다.
- 테스트 보강 — WallpaperSearchServiceTest(8개), RecommendationServiceTest(4개), WallpaperApiControllerTest(3개), ClaudeApiClientTest(2개) 추가로 핵심 비즈니스 로직 커버리지가 크게 향상되었습니다.
- Flutter AppError 구조화 — 서버 에러 코드와 Flutter AppErrorType이 연동되어 에러 처리 일관성이 확보되었습니다.

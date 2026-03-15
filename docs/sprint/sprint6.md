# Sprint 6 구현 계획 - 클라이언트 리팩토링 + 테스트

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 클라이언트 로컬 캐시(SharedPreferences), 구조화된 에러 처리, 무한 스크롤 페이지네이션을 구현하고, 서버 핵심 비즈니스 로직 테스트를 보강하며 Sprint 5 코드 리뷰 이슈(I-1~I-3)를 해소하여 M4(안정화 완료) 마일스톤을 달성한다.

**Architecture:** 클라이언트는 기존 `GameRepository` 싱글턴 인메모리 캐시를 `shared_preferences` 기반 영속 캐시로 교체한다. 에러 처리는 `AppError` 클래스 + 서버 표준 에러 응답(`{ "error": { "code": "...", "message": "..." } }`)으로 일원화한다. 페이지네이션은 기존 `PageView` 방식 대신 `ScrollController`를 활용한 무한 스크롤로 교체한다. 서버는 `ObjectMapper` static 상수화, `findAllTagged()` 페이지 처리, 컨트롤러 분리 세 가지 리팩토링을 적용한다.

**Tech Stack:** Flutter (Dart), Provider, shared_preferences ^2.2.0, http ^1.2.0, Spring Boot 3.x (Java 21), Spring Data JPA, JUnit 5, MockMvc, Mockito

---

## 브랜치 전략

- 브랜치: `sprint6` (master에서 분기, 이미 생성됨)

---

## 전제 조건 및 현재 상태

- `shared_preferences: ^2.2.0`이 `pubspec.yaml`에 이미 추가되어 있음
- 서버 패키지 루트: `server/src/main/java/com/gamepaper/`
- 클라이언트 루트: `client/lib/`
- `WallpaperApiController`가 `/api/wallpapers/{gameId}`, `/api/wallpapers/search`, `/api/wallpapers/{id}/like`, `/api/wallpapers/recommended` 네 가지 역할을 하나의 컨트롤러에서 담당 (I-3 이슈)
- `WallpaperSearchService.parseTagsFromJson()`이 매 호출마다 `new ObjectMapper()`를 생성 (I-1 이슈)
- `WallpaperRepository.findAllTagged()`가 DB 전체를 메모리로 로드 (I-2 이슈)
- `handle_error.dart`가 Firebase 에러 코드 문자열 매칭 방식으로 동작 중 (구조화 필요)
- `WallpaperScreen`은 `PageView` + `SmoothPageIndicator` 방식으로 페이지 단위 로드 중

---

## Task 1: 서버 코드 리뷰 이슈 해소 (I-1, I-2, I-3)

**목표:** Sprint 5 코드 리뷰에서 지적된 세 가지 중요(Important) 이슈를 해소한다.

**Files:**
- Modify: `server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`
- Modify: `server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`
- Create: `server/src/main/java/com/gamepaper/api/WallpaperLikeApiController.java`
- Create: `server/src/main/java/com/gamepaper/api/WallpaperRecommendApiController.java`
- Modify: `server/src/main/java/com/gamepaper/api/WallpaperApiController.java`
- Modify: `server/src/test/java/com/gamepaper/api/WallpaperSearchApiTest.java`

### Step 1: I-1 수정 — ObjectMapper static 상수로 변경

`server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`의 `parseTagsFromJson()` 메서드 내부 `new ObjectMapper()` 호출을 클래스 상단 static 상수로 이동한다.

```java
// 클래스 상단 (기존 코드에 이미 있는 경우 확인 후 적용)
private static final ObjectMapper MAPPER = new ObjectMapper();

// parseTagsFromJson() 내부:
// 기존: com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
// 변경: MAPPER 사용
String[] arr = MAPPER.readValue(tagsJson, String[].class);
```

### Step 2: I-2 수정 — findAllTagged() 페이지 처리

`WallpaperRepository`에 페이지 처리 오버로드를 추가한다.

```java
// WallpaperRepository.java에 추가
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Query("SELECT w FROM Wallpaper w WHERE w.tags IS NOT NULL ORDER BY w.createdAt DESC")
Page<Wallpaper> findAllTagged(Pageable pageable);
```

`WallpaperSearchService`의 검색 로직을 페이지 배치 방식으로 변경한다. `MAX_RESULTS` 제한이 있으므로 첫 페이지(500개)만 스캔하면 충분하다.

```java
private static final int SCAN_PAGE_SIZE = 500;

public List<Wallpaper> searchByTagsAnd(List<String> tags) {
    if (tags == null || tags.isEmpty()) return Collections.emptyList();

    Pageable pageable = PageRequest.of(0, SCAN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    List<Wallpaper> page = wallpaperRepository.findAllTagged(pageable).getContent();
    return page.stream()
            .filter(wp -> containsAllTags(wp.getTags(), tags))
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
}

public List<Wallpaper> searchByTagsOr(List<String> tags) {
    if (tags == null || tags.isEmpty()) return Collections.emptyList();

    Pageable pageable = PageRequest.of(0, SCAN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    List<Wallpaper> page = wallpaperRepository.findAllTagged(pageable).getContent();
    return page.stream()
            .filter(wp -> containsAnyTag(wp.getTags(), tags))
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
}
```

`getTagFrequency()` 메서드도 동일하게 수정한다.

```java
public Map<String, Long> getTagFrequency() {
    Pageable pageable = PageRequest.of(0, SCAN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    List<Wallpaper> page = wallpaperRepository.findAllTagged(pageable).getContent();
    return page.stream()
            .filter(wp -> wp.getTags() != null)
            .flatMap(wp -> parseTagsFromJson(wp.getTags()).stream())
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
            .entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    java.util.LinkedHashMap::new
            ));
}
```

### Step 3: I-3 수정 — 컨트롤러 분리

`WallpaperApiController`에서 좋아요/추천 엔드포인트를 분리한다.

`server/src/main/java/com/gamepaper/api/WallpaperLikeApiController.java` 생성:

```java
package com.gamepaper.api;

import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperLikeApiController {

    private final WallpaperRepository wallpaperRepository;
    private final UserLikeRepository userLikeRepository;

    @PostMapping("/{id}/like")
    public Map<String, Object> toggleLike(
            @PathVariable Long id,
            @RequestHeader(value = "device-id", required = false) String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "device-id 헤더가 필요합니다.");
        }

        if (!wallpaperRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "배경화면을 찾을 수 없습니다: " + id);
        }

        boolean alreadyLiked = userLikeRepository.existsByDeviceIdAndWallpaperId(deviceId, id);

        if (alreadyLiked) {
            userLikeRepository.deleteByDeviceIdAndWallpaperId(deviceId, id);
            long likeCount = userLikeRepository.countByWallpaperId(id);
            return Map.of("liked", false, "likeCount", likeCount);
        } else {
            userLikeRepository.save(new UserLike(deviceId, id));
            long likeCount = userLikeRepository.countByWallpaperId(id);
            return Map.of("liked", true, "likeCount", likeCount);
        }
    }
}
```

`server/src/main/java/com/gamepaper/api/WallpaperRecommendApiController.java` 생성:

```java
package com.gamepaper.api;

import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.domain.wallpaper.Wallpaper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperRecommendApiController {

    private final RecommendationService recommendationService;

    @GetMapping("/recommended")
    public List<WallpaperDto> getRecommended(
            @RequestHeader(value = "device-id", required = false) String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }

        List<Wallpaper> recommendations = recommendationService.recommend(deviceId);
        return recommendations.stream().map(WallpaperDto::new).collect(Collectors.toList());
    }
}
```

`WallpaperApiController.java`에서 `toggleLike()`, `getRecommended()` 메서드와 관련 의존성(`UserLikeRepository`, `RecommendationService`)을 제거한다.

### Step 4: 기존 테스트 실행 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.WallpaperSearchApiTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 2개 테스트 PASS

### Step 5: 커밋

```bash
git add server/src/main/java/com/gamepaper/api/ server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java
git commit -m "refactor: Sprint 5 코드 리뷰 이슈 해소 - ObjectMapper static화, findAllTagged 페이징, 컨트롤러 분리 (Task 1)"
```

---

## Task 2: 서버 에러 응답 표준화

**목표:** 모든 서버 API 에러 응답을 `{ "error": { "code": "...", "message": "..." } }` 구조로 통일한다.

**Files:**
- Create: `server/src/main/java/com/gamepaper/api/error/ErrorCode.java`
- Create: `server/src/main/java/com/gamepaper/api/error/ErrorResponse.java`
- Create: `server/src/main/java/com/gamepaper/api/error/GlobalExceptionHandler.java`
- Create: `server/src/test/java/com/gamepaper/api/ErrorResponseTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/api/ErrorResponseTest.java`:

```java
package com.gamepaper.api;

import com.gamepaper.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=local",
    "spring.sql.init.mode=never",
    "storage.root=${java.io.tmpdir}/gamepaper-test",
    "storage.base-url=http://localhost:8080"
})
class ErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void 존재하지않는_게임_404_구조화에러() throws Exception {
        mockMvc.perform(get("/api/wallpapers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString());
    }

    @Test
    void 태그없이_검색_400_구조화에러() throws Exception {
        mockMvc.perform(get("/api/wallpapers/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").isString());
    }
}
```

### Step 2: 테스트 실행하여 실패 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.ErrorResponseTest" -i
```

기대 결과: FAIL — `$.error.code` 경로 없음 (현재 Spring 기본 에러 응답 반환)

### Step 3: ErrorCode 열거형 구현

`server/src/main/java/com/gamepaper/api/error/ErrorCode.java`:

```java
package com.gamepaper.api.error;

public enum ErrorCode {
    GAME_NOT_FOUND,
    WALLPAPER_NOT_FOUND,
    INVALID_REQUEST,
    MISSING_DEVICE_ID,
    INTERNAL_ERROR
}
```

### Step 4: ErrorResponse DTO 구현

`server/src/main/java/com/gamepaper/api/error/ErrorResponse.java`:

```java
package com.gamepaper.api.error;

public record ErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String code, String message) {}

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new ErrorDetail(code.name(), message));
    }
}
```

### Step 5: GlobalExceptionHandler 구현

`server/src/main/java/com/gamepaper/api/error/GlobalExceptionHandler.java`:

```java
package com.gamepaper.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorCode code = resolveErrorCode(ex);
        ErrorResponse body = ErrorResponse.of(code, ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ErrorCode resolveErrorCode(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "";
        if (reason.contains("게임을 찾을 수 없습니다")) return ErrorCode.GAME_NOT_FOUND;
        if (reason.contains("배경화면을 찾을 수 없습니다")) return ErrorCode.WALLPAPER_NOT_FOUND;
        if (reason.contains("device-id")) return ErrorCode.MISSING_DEVICE_ID;
        if (ex.getStatusCode().value() == 400) return ErrorCode.INVALID_REQUEST;
        return ErrorCode.INTERNAL_ERROR;
    }
}
```

### Step 6: 테스트 실행하여 통과 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.ErrorResponseTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 2개 테스트 PASS

### Step 7: 커밋

```bash
git add server/src/main/java/com/gamepaper/api/error/ server/src/test/java/com/gamepaper/api/ErrorResponseTest.java
git commit -m "feat: 서버 에러 응답 표준화 - GlobalExceptionHandler + ErrorCode 구현 (Task 2)"
```

---

## Task 3: WallpaperSearchService 단위 테스트 강화

**목표:** `WallpaperSearchService`의 AND/OR 검색, 태그 파싱, 빈도 분석 로직을 단위 테스트로 커버한다.

**Files:**
- Create: `server/src/test/java/com/gamepaper/api/WallpaperSearchServiceTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/api/WallpaperSearchServiceTest.java`:

```java
package com.gamepaper.api;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WallpaperSearchServiceTest {

    @Mock
    private WallpaperRepository wallpaperRepository;

    @InjectMocks
    private WallpaperSearchService service;

    private Wallpaper wpDarkLandscape;
    private Wallpaper wpDarkCity;
    private Wallpaper wpBright;

    @BeforeEach
    void setUp() {
        wpDarkLandscape = new Wallpaper(1L, "img1.jpg", "http://example.com/img1.jpg");
        wpDarkLandscape.setTags("[\"dark\",\"landscape\"]");

        wpDarkCity = new Wallpaper(2L, "img2.jpg", "http://example.com/img2.jpg");
        wpDarkCity.setTags("[\"dark\",\"city\"]");

        wpBright = new Wallpaper(3L, "img3.jpg", "http://example.com/img3.jpg");
        wpBright.setTags("[\"bright\",\"landscape\"]");
    }

    @Test
    void AND검색_모든태그포함() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        List<Wallpaper> result = service.searchByTagsAnd(List.of("dark", "landscape"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void OR검색_하나라도포함() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        List<Wallpaper> result = service.searchByTagsOr(List.of("city", "bright"));

        assertThat(result).hasSize(2);
    }

    @Test
    void 빈태그목록_빈결과반환() {
        assertThat(service.searchByTagsAnd(List.of())).isEmpty();
        assertThat(service.searchByTagsOr(List.of())).isEmpty();
    }

    @Test
    void NULL태그목록_빈결과반환() {
        assertThat(service.searchByTagsAnd(null)).isEmpty();
    }

    @Test
    void parseTagsFromJson_정상파싱() {
        List<String> tags = service.parseTagsFromJson("[\"dark\",\"landscape\",\"blue-tone\"]");
        assertThat(tags).containsExactly("dark", "landscape", "blue-tone");
    }

    @Test
    void parseTagsFromJson_빈문자열_빈목록반환() {
        assertThat(service.parseTagsFromJson("")).isEmpty();
        assertThat(service.parseTagsFromJson(null)).isEmpty();
    }

    @Test
    void parseTagsFromJson_잘못된JSON_빈목록반환() {
        assertThat(service.parseTagsFromJson("not-json")).isEmpty();
    }

    @Test
    void 태그빈도_분석() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        Map<String, Long> freq = service.getTagFrequency();

        assertThat(freq.get("dark")).isEqualTo(2L);
        assertThat(freq.get("landscape")).isEqualTo(2L);
        assertThat(freq.get("city")).isEqualTo(1L);
    }
}
```

### Step 2: 테스트 실행

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.WallpaperSearchServiceTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 7개 테스트 PASS

### Step 3: 커밋

```bash
git add server/src/test/java/com/gamepaper/api/WallpaperSearchServiceTest.java
git commit -m "test: WallpaperSearchService 단위 테스트 강화 - AND/OR 검색, 태그 파싱, 빈도 분석 (Task 3)"
```

---

## Task 4: RecommendationService 단위 테스트 강화

**목표:** `RecommendationService`의 추천 로직(좋아요 이력 없음, 태그 분석, 이미 좋아요한 항목 제외)을 단위 테스트로 커버한다.

**Files:**
- Modify: `server/src/test/java/com/gamepaper/api/RecommendationServiceTest.java`

### Step 1: 기존 테스트 파일 확인 후 테스트 추가

`server/src/test/java/com/gamepaper/api/RecommendationServiceTest.java`에 다음 테스트 케이스를 추가한다:

```java
package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private UserLikeRepository userLikeRepository;

    @Mock
    private WallpaperRepository wallpaperRepository;

    @Mock
    private WallpaperSearchService searchService;

    @InjectMocks
    private RecommendationService recommendationService;

    private Wallpaper likedWallpaper;
    private Wallpaper candidateWallpaper;

    @BeforeEach
    void setUp() {
        likedWallpaper = new Wallpaper(1L, "liked.jpg", "http://example.com/liked.jpg");
        likedWallpaper.setTags("[\"dark\",\"landscape\"]");

        candidateWallpaper = new Wallpaper(2L, "candidate.jpg", "http://example.com/candidate.jpg");
        candidateWallpaper.setTags("[\"dark\",\"city\"]");
    }

    @Test
    void 좋아요이력없으면_빈목록반환() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of());

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).isEmpty();
    }

    @Test
    void 좋아요태그기반_추천반환() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(1L));
        when(wallpaperRepository.findAllById(List.of(1L))).thenReturn(List.of(likedWallpaper));
        when(searchService.parseTagsFromJson("[\"dark\",\"landscape\"]"))
                .thenReturn(List.of("dark", "landscape"));
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(candidateWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void 이미좋아요한항목_추천목록에서제외() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(1L));
        when(wallpaperRepository.findAllById(List.of(1L))).thenReturn(List.of(likedWallpaper));
        when(searchService.parseTagsFromJson(anyString())).thenReturn(List.of("dark"));
        // 후보에 이미 좋아요한 wallpaper(id=1)가 포함
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(likedWallpaper, candidateWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L); // id=1 제외 확인
    }

    @Test
    void 태그없는좋아요이력_빈목록반환() {
        Wallpaper noTagWallpaper = new Wallpaper(3L, "notag.jpg", "http://example.com/notag.jpg");
        // tags = null
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(3L));
        when(wallpaperRepository.findAllById(List.of(3L))).thenReturn(List.of(noTagWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).isEmpty();
    }
}
```

### Step 2: 테스트 실행

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.RecommendationServiceTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 4개 테스트 PASS

### Step 3: 커밋

```bash
git add server/src/test/java/com/gamepaper/api/RecommendationServiceTest.java
git commit -m "test: RecommendationService 단위 테스트 강화 - 좋아요 이력, 태그 분석, 중복 제외 (Task 4)"
```

---

## Task 5: REST API 컨트롤러 테스트 (MockMvc)

**목표:** `WallpaperApiController`, `GameApiController`, `WallpaperLikeApiController`의 HTTP 레벨 동작을 MockMvc로 검증한다.

**Files:**
- Create: `server/src/test/java/com/gamepaper/api/WallpaperApiControllerTest.java`
- Modify: `server/src/test/java/com/gamepaper/api/GameApiControllerTest.java` (기존 파일에 케이스 추가)

### Step 1: WallpaperApiControllerTest 작성

`server/src/test/java/com/gamepaper/api/WallpaperApiControllerTest.java`:

```java
package com.gamepaper.api;

import com.gamepaper.api.dto.PagedResponse;
import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.config.DataInitializer;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=local",
    "spring.sql.init.mode=never",
    "storage.root=${java.io.tmpdir}/gamepaper-test",
    "storage.base-url=http://localhost:8080"
})
class WallpaperApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private GameRepository gameRepository;

    @MockBean
    private WallpaperSearchService searchService;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void 게임ID로_배경화면_페이지_조회() throws Exception {
        when(gameRepository.existsById(1L)).thenReturn(true);
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        when(wallpaperRepository.findAllByGameId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wp)));

        mockMvc.perform(get("/api/wallpapers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 존재하지않는_게임ID_404에러() throws Exception {
        when(gameRepository.existsById(99999L)).thenReturn(false);

        mockMvc.perform(get("/api/wallpapers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void 페이지_파라미터_적용() throws Exception {
        when(gameRepository.existsById(1L)).thenReturn(true);
        when(wallpaperRepository.findAllByGameId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/wallpapers/1").param("page", "2").param("size", "6"))
                .andExpect(status().isOk());
    }
}
```

### Step 2: 테스트 실행

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.WallpaperApiControllerTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 3개 테스트 PASS

### Step 3: 커밋

```bash
git add server/src/test/java/com/gamepaper/api/WallpaperApiControllerTest.java
git commit -m "test: WallpaperApiController MockMvc 테스트 추가 - 페이지 조회, 404 에러, 페이지 파라미터 (Task 5)"
```

---

## Task 6: Claude API 클라이언트 테스트 (mock 응답)

**목표:** `ClaudeApiClient`의 태그 파싱 로직과 에러 처리를 mock 응답으로 단위 테스트한다.

**Files:**
- Create: `server/src/test/java/com/gamepaper/claude/ClaudeApiClientTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/claude/ClaudeApiClientTest.java`:

```java
package com.gamepaper.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ClaudeApiClient 단위 테스트.
 * 실제 API 호출 없이 태그 파싱 메서드와 에러 처리 로직을 검증한다.
 * RestClient는 통합 테스트 대상이므로 여기서는 mock하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ClaudeApiClientTest {

    @Mock
    private CrawlerStrategyParser parser;

    // RestClient.Builder mock은 생성자 주입이 어려우므로
    // parseTagsFromResponse는 private → 테스트 가능한 구조로 리팩토링 필요시 별도 Task
    // 이 테스트에서는 API 키 미설정 예외와 공개 메서드를 검증한다.

    @Test
    void API키_미설정시_analyzeHtml_예외발생() {
        // ClaudeApiClient를 직접 생성 (RestClient.Builder는 noop mock 사용)
        org.springframework.web.client.RestClient.Builder builder =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.Builder.class);
        org.springframework.web.client.RestClient mockRestClient =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.class);
        org.mockito.Mockito.when(builder.build()).thenReturn(mockRestClient);

        ClaudeApiClient client = new ClaudeApiClient(parser, builder);
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.analyzeHtml("<html/>", "https://example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void API키_미설정시_generateTagsFromImage_예외발생() {
        org.springframework.web.client.RestClient.Builder builder =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.Builder.class);
        org.springframework.web.client.RestClient mockRestClient =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.class);
        org.mockito.Mockito.when(builder.build()).thenReturn(mockRestClient);

        ClaudeApiClient client = new ClaudeApiClient(parser, builder);
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.generateTagsFromImage(new byte[0], "jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
```

### Step 2: 테스트 실행

```bash
cd server && ./gradlew test --tests "com.gamepaper.claude.ClaudeApiClientTest" -i
```

기대 결과: `BUILD SUCCESSFUL`, 2개 테스트 PASS

### Step 3: 전체 서버 테스트 실행

```bash
cd server && ./gradlew test -i
```

기대 결과: `BUILD SUCCESSFUL`, 전체 테스트 PASS

### Step 4: 커밋

```bash
git add server/src/test/java/com/gamepaper/claude/ClaudeApiClientTest.java
git commit -m "test: ClaudeApiClient 단위 테스트 - API 키 미설정 예외 검증 (Task 6)"
```

---

## Task 7: 클라이언트 AppError 구조화 + 서버 에러 코드 매핑

**목표:** `AppError` 클래스를 정의하고, `GameRepository`가 서버 에러 응답을 파싱하여 구조화된 예외를 던지도록 교체한다. `handle_error.dart`의 문자열 매칭 방식을 에러 코드 기반으로 전환한다.

**Files:**
- Create: `client/lib/errors/app_error.dart`
- Modify: `client/lib/repositories/game_repository.dart`
- Modify: `client/lib/utils/handle_error.dart`
- Modify: `client/lib/widgets/common/error_display.dart`

### Step 1: AppError 클래스 작성

`client/lib/errors/app_error.dart`:

```dart
/// 앱 전역 에러 클래스.
/// 서버 API 에러 응답: { "error": { "code": "...", "message": "..." } }
/// 에러 코드 기반으로 사용자 메시지를 매핑한다.
class AppError implements Exception {
  final String code;
  final String message;
  final AppErrorType type;

  const AppError({
    required this.code,
    required this.message,
    required this.type,
  });

  /// 서버 에러 응답 JSON에서 AppError 생성.
  /// 응답 바디: { "error": { "code": "GAME_NOT_FOUND", "message": "..." } }
  factory AppError.fromServerJson(Map<String, dynamic> json, int statusCode) {
    final errorObj = json['error'] as Map<String, dynamic>?;
    final code = errorObj?['code'] as String? ?? 'UNKNOWN';
    final message = errorObj?['message'] as String? ?? '알 수 없는 오류';
    return AppError(
      code: code,
      message: message,
      type: _resolveType(statusCode, code),
    );
  }

  /// 네트워크/타임아웃 에러에서 AppError 생성.
  factory AppError.network(String detail) {
    return AppError(
      code: 'NETWORK_ERROR',
      message: detail,
      type: AppErrorType.network,
    );
  }

  /// 타임아웃 에러에서 AppError 생성.
  factory AppError.timeout() {
    return AppError(
      code: 'TIMEOUT',
      message: '요청 시간이 초과되었습니다.',
      type: AppErrorType.timeout,
    );
  }

  /// 사용자에게 표시할 메시지.
  String get userMessage => _toUserMessage(code, type);

  static AppErrorType _resolveType(int statusCode, String code) {
    if (statusCode == 404) return AppErrorType.notFound;
    if (statusCode == 400) return AppErrorType.invalidRequest;
    if (statusCode >= 500) return AppErrorType.server;
    return AppErrorType.unknown;
  }

  static String _toUserMessage(String code, AppErrorType type) {
    switch (code) {
      case 'GAME_NOT_FOUND':
        return '게임을 찾을 수 없습니다.';
      case 'WALLPAPER_NOT_FOUND':
        return '배경화면을 찾을 수 없습니다.';
      case 'INVALID_REQUEST':
        return '잘못된 요청입니다.';
      case 'MISSING_DEVICE_ID':
        return '기기 정보를 확인할 수 없습니다.';
      case 'NETWORK_ERROR':
        return '인터넷 연결이 원활하지 않습니다. 네트워크를 확인해주세요.';
      case 'TIMEOUT':
        return '요청 시간이 초과되었습니다. 다시 시도해주세요.';
      case 'no-games-available':
        return '현재 이용할 수 있는 게임이 없습니다.';
      case 'no-wallpapers-available':
        return '사용할 수 있는 배경화면이 없습니다.';
      case 'no-wallpapers-for-this-page':
        return '이 페이지에는 배경화면이 없습니다.';
      default:
        return '오류가 발생했습니다. 다시 시도해주세요.';
    }
  }

  @override
  String toString() => 'AppError(code: $code, type: $type, message: $message)';
}

enum AppErrorType {
  network,
  timeout,
  notFound,
  invalidRequest,
  server,
  unknown,
}
```

### Step 2: GameRepository에 에러 처리 통합

`client/lib/repositories/game_repository.dart`의 `fetchGameList()`, `getWallpapersForPage()` 메서드에서 비-200 응답을 `AppError`로 변환한다.

`fetchGameList()` 수정:

```dart
import 'dart:io';
import 'package:gamepaper/errors/app_error.dart';

// 기존 import 유지

Future<List<Game>> fetchGameList() async {
  if (_gameCache.containsKey(0)) {
    return _gameCache[0]!;
  }

  try {
    final response = await http
        .get(
          Uri.parse(ApiConfig.gamesUrl()),
          headers: {'Content-Type': 'application/json'},
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200) {
      final List<dynamic> data = json.decode(response.body);
      final games = data.map((json) => Game.fromServerJson(json)).toList();
      _gameCache[0] = games;
      return games;
    } else {
      final body = json.decode(response.body) as Map<String, dynamic>;
      throw AppError.fromServerJson(body, response.statusCode);
    }
  } on TimeoutException {
    throw AppError.timeout();
  } on SocketException catch (e) {
    throw AppError.network(e.message);
  }
}
```

`getWallpapersForPage()` 수정 (동일한 패턴 적용):

```dart
Future<List<Wallpaper>> getWallpapersForPage(
  int gameId,
  int page,
  int wallpapersPerPage,
) async {
  final cacheKey = '${gameId}_$page';
  if (_wallpaperCache.containsKey(cacheKey)) {
    return _wallpaperCache[cacheKey]!;
  }

  try {
    final serverPage = page - 1;
    final response = await http
        .get(
          Uri.parse(ApiConfig.wallpapersUrl(
            gameId,
            page: serverPage,
            size: wallpapersPerPage,
          )),
          headers: {'Content-Type': 'application/json'},
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200) {
      final Map<String, dynamic> data = json.decode(response.body);
      final List<dynamic> content = data['content'];
      final wallpapers =
          content.map((json) => Wallpaper.fromServerJson(json)).toList();
      _wallpaperCache[cacheKey] = wallpapers;
      return wallpapers;
    } else {
      final body = json.decode(response.body) as Map<String, dynamic>;
      throw AppError.fromServerJson(body, response.statusCode);
    }
  } on TimeoutException {
    throw AppError.timeout();
  } on SocketException catch (e) {
    throw AppError.network(e.message);
  }
}
```

### Step 3: handle_error.dart를 AppError 기반으로 교체

`client/lib/utils/handle_error.dart`:

```dart
import 'package:gamepaper/errors/app_error.dart';

/// 에러 객체를 사용자 표시 메시지로 변환한다.
/// AppError인 경우 구조화된 userMessage를 반환하고,
/// 그 외 레거시 예외는 코드 기반으로 fallback 처리한다.
String handleError(Object error) {
  if (error is AppError) {
    return error.userMessage;
  }

  // 레거시 문자열 에러 (직접 throw된 Exception 메시지) 처리
  final errorMessage = error.toString();
  if (errorMessage.contains('no-games-available')) {
    return '현재 이용할 수 있는 게임이 없습니다. 나중에 다시 확인해주세요.';
  } else if (errorMessage.contains('no-wallpapers-available')) {
    return '사용할 수 있는 배경화면이 없습니다.';
  } else if (errorMessage.contains('no-wallpapers-for-this-page')) {
    return '이 페이지에는 배경화면이 없습니다.';
  }

  return '오류가 발생했습니다. 다시 시도해주세요.';
}
```

### Step 4: 빌드 확인

```bash
cd client && flutter analyze
```

기대 결과: 에러 없음

### Step 5: 커밋

```bash
git add client/lib/errors/ client/lib/repositories/game_repository.dart client/lib/utils/handle_error.dart
git commit -m "feat: AppError 구조화 + GameRepository 에러 처리 통합 + handle_error 코드 기반 전환 (Task 7)"
```

---

## Task 8: 클라이언트 SharedPreferences 영속 캐시

**목표:** 앱 재시작 후 게임 목록을 `shared_preferences`에서 즉시 로드하고, 백그라운드에서 API를 갱신하는 캐시 레이어를 구현한다. 배경화면 메타데이터는 TTL 1시간 캐시를 적용한다.

**Files:**
- Create: `client/lib/cache/local_cache.dart`
- Modify: `client/lib/repositories/game_repository.dart`
- Modify: `client/lib/providers/home_provider.dart`

### Step 1: LocalCache 서비스 작성

`client/lib/cache/local_cache.dart`:

```dart
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// SharedPreferences 기반 로컬 캐시 서비스.
///
/// 저장 구조:
/// - cache__{key}        : JSON 문자열 (게임 목록 등)
/// - cache_ts__{key}     : 저장 시각 (Unix timestamp ms)
///
/// TTL(Time-To-Live): 만료된 캐시는 읽기 시 null 반환.
class LocalCache {
  static const String _keyPrefix = 'cache__';
  static const String _tsPrefix = 'cache_ts__';

  static const String keyGameList = 'game_list';
  static const String keyPrefix_wallpaperPage = 'wallpaper_page__';

  /// [key]에 해당하는 캐시 데이터를 반환한다.
  /// [ttlMs]가 설정된 경우 만료된 캐시는 null 반환.
  Future<String?> get(String key, {int? ttlMs}) async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getString('$_keyPrefix$key');
    if (value == null) return null;

    if (ttlMs != null) {
      final ts = prefs.getInt('$_tsPrefix$key') ?? 0;
      final now = DateTime.now().millisecondsSinceEpoch;
      if (now - ts > ttlMs) {
        return null; // 만료
      }
    }
    return value;
  }

  /// [key]에 JSON 문자열을 저장한다.
  Future<void> set(String key, String jsonValue) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('$_keyPrefix$key', jsonValue);
    await prefs.setInt('$_tsPrefix$key', DateTime.now().millisecondsSinceEpoch);
  }

  /// [key] 캐시를 삭제한다.
  Future<void> remove(String key) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('$_keyPrefix$key');
    await prefs.remove('$_tsPrefix$key');
  }

  /// 배경화면 페이지 캐시 키를 반환한다.
  static String wallpaperPageKey(int gameId, int page) =>
      '$keyPrefix_wallpaperPage${gameId}_$page';
}
```

### Step 2: GameRepository에 영속 캐시 통합

`client/lib/repositories/game_repository.dart`에 `LocalCache` 의존성을 추가하고 `fetchGameList()`를 다음과 같이 수정한다:

```dart
import 'package:gamepaper/cache/local_cache.dart';

// 싱글턴 내부에 _localCache 필드 추가
final LocalCache _localCache = LocalCache();

/// 게임 목록 조회 (캐시 우선 → 백그라운드 갱신 전략).
/// 1. SharedPreferences 캐시에서 즉시 반환
/// 2. 백그라운드에서 API 호출 → 캐시 갱신 (onRefresh 콜백 호출)
Future<List<Game>> fetchGameList({void Function(List<Game>)? onRefresh}) async {
  // 1. 인메모리 캐시 (동일 세션 내)
  if (_gameCache.containsKey(0)) {
    return _gameCache[0]!;
  }

  // 2. SharedPreferences 영속 캐시
  final cached = await _localCache.get(LocalCache.keyGameList);
  if (cached != null) {
    final List<dynamic> data = json.decode(cached);
    final games = data.map((j) => Game.fromServerJson(j as Map<String, dynamic>)).toList();
    _gameCache[0] = games;

    // 백그라운드 갱신 (캐시 반환 후 비동기 업데이트)
    _refreshGameListInBackground(onRefresh);
    return games;
  }

  // 3. 캐시 없음 → API 직접 호출
  return _fetchGameListFromApi();
}

void _refreshGameListInBackground(void Function(List<Game>)? onRefresh) {
  _fetchGameListFromApi().then((games) {
    if (onRefresh != null) onRefresh(games);
  }).catchError((_) {
    // 백그라운드 갱신 실패는 무시 (이미 캐시 데이터 반환됨)
  });
}

Future<List<Game>> _fetchGameListFromApi() async {
  try {
    final response = await http
        .get(
          Uri.parse(ApiConfig.gamesUrl()),
          headers: {'Content-Type': 'application/json'},
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200) {
      final List<dynamic> data = json.decode(response.body);
      final games = data.map((j) => Game.fromServerJson(j as Map<String, dynamic>)).toList();
      _gameCache[0] = games;
      // 캐시 저장
      await _localCache.set(LocalCache.keyGameList, response.body);
      return games;
    } else {
      final body = json.decode(response.body) as Map<String, dynamic>;
      throw AppError.fromServerJson(body, response.statusCode);
    }
  } on TimeoutException {
    throw AppError.timeout();
  } on SocketException catch (e) {
    throw AppError.network(e.message);
  }
}
```

배경화면 TTL 캐시 (`getWallpapersForPage()`):

```dart
// getWallpapersForPage() 시작 부분에 SharedPreferences 캐시 확인 추가
static const int _wallpaperCacheTtlMs = 60 * 60 * 1000; // 1시간

// 인메모리 캐시 확인 후 SharedPreferences 확인:
final persistKey = LocalCache.wallpaperPageKey(gameId, page);
final cachedStr = await _localCache.get(persistKey, ttlMs: _wallpaperCacheTtlMs);
if (cachedStr != null) {
  final List<dynamic> data = json.decode(cachedStr);
  final wallpapers = data.map((j) => Wallpaper.fromServerJson(j as Map<String, dynamic>)).toList();
  _wallpaperCache[cacheKey] = wallpapers;
  return wallpapers;
}

// API 호출 후 캐시 저장:
// 성공 응답 처리 후
await _localCache.set(persistKey, json.encode(data['content']));
```

캐시 초기화 시 영속 캐시도 정리:

```dart
Future<void> clearCache() async {
  _gameCache.clear();
  _wallpaperCache.clear();
  await _localCache.remove(LocalCache.keyGameList);
}
```

### Step 3: HomeProvider에 캐시 갱신 콜백 연결

`client/lib/providers/home_provider.dart`:

```dart
Future<void> loadGames() async {
  _setLoadingState(true);
  _setErrorMessage('');

  try {
    final games = await _repository.fetchGameList(
      onRefresh: (refreshedGames) {
        // 백그라운드 갱신 완료 시 UI 업데이트
        _gameMap = _groupGamesByAlphabet(refreshedGames);
        _setLoadingState(false);
        notifyListeners();
      },
    );
    _gameMap = _groupGamesByAlphabet(games);
  } catch (e) {
    _setErrorMessage(e.toString());
  }

  _setLoadingState(false);
}
```

### Step 4: 오프라인 감지 및 안내 추가

`client/lib/providers/home_provider.dart`에 오프라인 상태 필드 추가:

```dart
bool _isOffline = false;
bool get isOffline => _isOffline;

// loadGames() catch 블록:
} catch (e) {
  if (e is AppError && e.type == AppErrorType.network) {
    _isOffline = true;
    // 캐시 데이터가 있으면 에러 메시지 없이 오프라인 안내만 표시
    if (_gameMap.isEmpty) {
      _setErrorMessage(e.userMessage);
    }
  } else {
    _setErrorMessage(e.toString());
  }
}
```

`client/lib/screens/home_screen.dart`에 오프라인 배너 추가 (Consumer에서 `homeProvider.isOffline` 확인):

```dart
// AppBar 아래 또는 상단에 배너 위젯 조건부 표시
if (homeProvider.isOffline)
  Container(
    color: Colors.orange.shade700,
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: const Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.wifi_off, size: 14, color: Colors.white),
        SizedBox(width: 4),
        Text('오프라인 모드 — 캐시된 데이터 표시 중',
            style: TextStyle(color: Colors.white, fontSize: 12)),
      ],
    ),
  ),
```

### Step 5: 빌드 확인

```bash
cd client && flutter analyze
```

기대 결과: 에러 없음

### Step 6: 커밋

```bash
git add client/lib/cache/ client/lib/repositories/game_repository.dart client/lib/providers/home_provider.dart client/lib/screens/home_screen.dart
git commit -m "feat: SharedPreferences 영속 캐시 + 오프라인 모드 안내 구현 (Task 8)"
```

---

## Task 9: 클라이언트 무한 스크롤 페이지네이션

**목표:** `WallpaperScreen`의 `PageView` 방식을 무한 스크롤(`ScrollController` + "더 보기")로 교체한다. 스크롤 끝에 도달하면 다음 페이지를 자동 로드한다.

**Files:**
- Modify: `client/lib/providers/wallpaper_provider.dart`
- Modify: `client/lib/screens/wallpaper_screen.dart`

### Step 1: WallpaperProvider 무한 스크롤 지원으로 확장

`client/lib/providers/wallpaper_provider.dart`:

```dart
import 'package:flutter/foundation.dart';
import 'package:gamepaper/models/game.dart';
import '../repositories/game_repository.dart';

/// 배경화면 무한 스크롤 상태 관리 Provider.
///
/// - 초기 로드: 1페이지 (12개)
/// - 스크롤 끝 도달 시: loadNextPage() 호출 → 다음 페이지 로드 및 목록에 추가
/// - 더 이상 데이터 없으면 hasMore = false
class WallpaperProvider with ChangeNotifier {
  final Game game;
  final GameRepository _repository;
  final int wallpapersPerPage = 12;

  final List<Wallpaper> _wallpapers = [];
  bool _isLoading = false;
  bool _hasMore = true;
  int _currentPage = 1; // 1-indexed
  String? _errorMessage;

  WallpaperProvider({required this.game, required GameRepository repository})
      : _repository = repository;

  List<Wallpaper> get wallpapers => List.unmodifiable(_wallpapers);
  bool get isLoading => _isLoading;
  bool get hasMore => _hasMore;
  String? get errorMessage => _errorMessage;

  /// 첫 페이지 로드.
  Future<void> loadInitial() async {
    _wallpapers.clear();
    _currentPage = 1;
    _hasMore = true;
    _errorMessage = null;
    await _loadPage(_currentPage);
  }

  /// 다음 페이지 로드.
  Future<void> loadNextPage() async {
    if (_isLoading || !_hasMore) return;
    await _loadPage(_currentPage + 1);
  }

  Future<void> _loadPage(int page) async {
    _isLoading = true;
    notifyListeners();

    try {
      final newItems = await _repository.getWallpapersForPage(
        game.id,
        page,
        wallpapersPerPage,
      );

      if (newItems.isEmpty || newItems.length < wallpapersPerPage) {
        _hasMore = false;
      }

      _wallpapers.addAll(newItems);
      _currentPage = page;
      _errorMessage = null;
    } catch (e) {
      _errorMessage = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
```

### Step 2: WallpaperScreen 무한 스크롤 구현

`client/lib/screens/wallpaper_screen.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:gamepaper/repositories/game_repository.dart';
import 'package:gamepaper/widgets/common/error_display.dart';
import 'package:gamepaper/widgets/common/loading_widget.dart';
import 'package:provider/provider.dart';
import 'package:gamepaper/models/game.dart';
import 'package:gamepaper/providers/wallpaper_provider.dart';
import 'package:gamepaper/providers/tag_filter_provider.dart';
import 'package:gamepaper/widgets/wallpaper/wallpaper_card.dart';
import 'package:gamepaper/widgets/wallpaper/tag_filter_chips.dart';

class WallpaperScreen extends StatefulWidget {
  final Game game;

  const WallpaperScreen({super.key, required this.game});

  @override
  State<WallpaperScreen> createState() => _WallpaperScreenState();
}

class _WallpaperScreenState extends State<WallpaperScreen> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll(WallpaperProvider provider) {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 300) {
      provider.loadNextPage();
    }
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => WallpaperProvider(
            game: widget.game,
            repository: GameRepository(),
          )..loadInitial(),
        ),
        ChangeNotifierProvider(
          create: (_) =>
              TagFilterProvider(repository: GameRepository())..loadTags(),
        ),
      ],
      child: Scaffold(
        backgroundColor: Colors.black,
        body: SafeArea(
          child: Consumer2<WallpaperProvider, TagFilterProvider>(
            builder: (context, wallpaperProvider, tagProvider, child) {
              // 태그 필터 활성화 시 필터링된 결과 표시
              if (tagProvider.isFilterActive) {
                return Column(
                  children: [
                    const TagFilterChips(),
                    if (tagProvider.isLoading)
                      const Expanded(
                          child: Center(child: CircularProgressIndicator()))
                    else if (tagProvider.filteredWallpapers.isEmpty)
                      const Expanded(
                        child: Center(
                          child: Text(
                            '검색 결과가 없습니다.',
                            style: TextStyle(color: Colors.grey),
                          ),
                        ),
                      )
                    else
                      Expanded(
                        child: _buildGrid(tagProvider.filteredWallpapers, null),
                      ),
                  ],
                );
              }

              // 초기 로딩 상태
              if (wallpaperProvider.wallpapers.isEmpty &&
                  wallpaperProvider.isLoading) {
                return const Center(child: LoadingWidget());
              }

              // 에러 상태 (데이터 없음)
              if (wallpaperProvider.wallpapers.isEmpty &&
                  wallpaperProvider.errorMessage != null) {
                return ErrorDisplayWidget(
                  error: wallpaperProvider.errorMessage!,
                  onRetry: wallpaperProvider.loadInitial,
                );
              }

              // 무한 스크롤 그리드
              _scrollController.removeListener(() {});
              _scrollController.addListener(() => _onScroll(wallpaperProvider));

              return Column(
                children: [
                  const TagFilterChips(),
                  Expanded(
                    child: _buildGrid(
                      wallpaperProvider.wallpapers,
                      wallpaperProvider,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildGrid(List<Wallpaper> wallpapers, WallpaperProvider? provider) {
    return GridView.builder(
      controller: provider != null ? _scrollController : null,
      padding: const EdgeInsets.all(8),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
        childAspectRatio: 9 / 16,
      ),
      // 무한 스크롤: 목록 끝에 로딩 인디케이터 또는 "끝" 표시 추가
      itemCount: provider != null
          ? wallpapers.length + (provider.hasMore ? 1 : 0)
          : wallpapers.length,
      itemBuilder: (_, i) {
        if (provider != null && i == wallpapers.length) {
          // 마지막 아이템: 로딩 인디케이터 또는 "더 이상 없음"
          return provider.isLoading
              ? const Center(
                  child: Padding(
                    padding: EdgeInsets.all(16),
                    child: CircularProgressIndicator(
                      color: Colors.white54,
                      strokeWidth: 2,
                    ),
                  ),
                )
              : const SizedBox.shrink();
        }
        return WallpaperCard(wallpaper: wallpapers[i]);
      },
    );
  }
}
```

> **참고:** `smooth_page_indicator` 의존성은 `pubspec.yaml`에서 제거하거나 유지해도 무방하다. 이번 스프린트에서는 제거하지 않고 orphan import만 정리한다.

### Step 3: 빌드 확인

```bash
cd client && flutter analyze
```

기대 결과: 에러 없음

### Step 4: 커밋

```bash
git add client/lib/providers/wallpaper_provider.dart client/lib/screens/wallpaper_screen.dart
git commit -m "feat: WallpaperProvider 무한 스크롤 + WallpaperScreen PageView → GridView 교체 (Task 9)"
```

---

## Task 10: GitHub Actions 테스트 파이프라인 확인 및 전체 검증

**목표:** 서버 전체 테스트가 GitHub Actions에서 통과하는지 확인하고, API 레벨 검증을 수행한다.

**Files:**
- Read: `.github/workflows/ci.yml`
- 필요시 Modify

### Step 1: 전체 서버 테스트 실행

```bash
cd server && ./gradlew test
```

기대 결과: `BUILD SUCCESSFUL` — 전체 테스트 PASS

테스트 커버리지 확인 (선택):

```bash
cd server && ./gradlew test jacocoTestReport
# 리포트 위치: server/build/reports/jacoco/test/html/index.html
```

### Step 2: 로컬 서버 API 검증 (Docker 실행 중인 경우)

```bash
# 배경화면 페이지 조회
curl -s http://localhost:8080/api/wallpapers/1?page=0&size=12 | python -m json.tool | head -30

# 에러 응답 구조 확인
curl -s http://localhost:8080/api/wallpapers/99999 | python -m json.tool
# 기대: { "error": { "code": "GAME_NOT_FOUND", "message": "..." } }

# 태그 검색
curl -s "http://localhost:8080/api/wallpapers/search?tags=dark" | python -m json.tool | head -20
```

### Step 3: Flutter 빌드 확인

```bash
cd client && flutter build apk --debug
```

기대 결과: 빌드 성공

### Step 4: CI yml 확인 및 필요시 수정

`.github/workflows/ci.yml`에 서버 테스트 스텝이 포함되어 있는지 확인한다. 없으면 추가:

```yaml
- name: 서버 테스트
  run: cd server && ./gradlew test
```

### Step 5: 최종 커밋

```bash
git add .
git commit -m "docs: Sprint 6 완료 - flow.md 업데이트 및 최종 정리 (Task 10)"
```

---

## 완료 기준 (Definition of Done)

- ✅ `WallpaperSearchService` — `ObjectMapper` static 상수, `findAllTagged()` 페이지 처리
- ✅ `WallpaperApiController` → `WallpaperLikeApiController` + `WallpaperRecommendApiController` 분리
- ✅ `GlobalExceptionHandler` — 모든 API 에러가 `{ "error": { "code": "...", "message": "..." } }` 형태로 반환
- ✅ 앱 재시작 후 게임 목록이 `shared_preferences` 캐시에서 즉시 표시됨 (API 응답 대기 없음)
- ✅ 배경화면 목록이 무한 스크롤로 12개씩 로드됨
- ✅ 네트워크 에러/서버 에러 시 `AppError.userMessage` 기반 사용자 메시지 표시
- ✅ 서버 단위 테스트: `WallpaperSearchServiceTest` (7개), `RecommendationServiceTest` (4개), `WallpaperApiControllerTest` (3개), `ErrorResponseTest` (2개), `ClaudeApiClientTest` (2개)
- ✅ GitHub Actions CI에서 전체 서버 테스트 통과
- ✅ `flutter analyze` 에러 없음

---

## 예상 산출물

| 파일 | 설명 |
|------|------|
| `server/.../api/error/ErrorCode.java` | 에러 코드 열거형 |
| `server/.../api/error/ErrorResponse.java` | 에러 응답 DTO |
| `server/.../api/error/GlobalExceptionHandler.java` | 전역 예외 처리기 |
| `server/.../api/WallpaperLikeApiController.java` | 좋아요 전용 컨트롤러 |
| `server/.../api/WallpaperRecommendApiController.java` | 추천 전용 컨트롤러 |
| `client/lib/errors/app_error.dart` | 클라이언트 구조화 에러 클래스 |
| `client/lib/cache/local_cache.dart` | SharedPreferences 캐시 레이어 |
| `server/src/test/.../WallpaperSearchServiceTest.java` | 검색 서비스 단위 테스트 |
| `server/src/test/.../RecommendationServiceTest.java` | 추천 서비스 단위 테스트 (강화) |
| `server/src/test/.../WallpaperApiControllerTest.java` | 컨트롤러 MockMvc 테스트 |
| `server/src/test/.../ErrorResponseTest.java` | 에러 응답 구조 테스트 |
| `server/src/test/.../ClaudeApiClientTest.java` | Claude API 클라이언트 테스트 |

---

## 리스크 및 대응 방안

| 리스크 | 영향 | 대응 |
|--------|------|------|
| `WallpaperApiController`에서 메서드 분리 시 URL 매핑 충돌 | 중 | `@RequestMapping` 중복 확인, 통합 테스트로 검증 |
| `findAllTagged(Pageable)` 오버로드 시 기존 `findAllTagged()` 호출부 컴파일 에러 | 중 | `findAllTagged()` 시그니처 유지 또는 호출부 일괄 수정 |
| SharedPreferences 직렬화 — `Wallpaper.fromServerJson()` 필드 변경 시 캐시 파싱 실패 | 낮 | `try-catch`로 파싱 실패 시 캐시 무효화 처리 |
| 무한 스크롤 `ScrollController` 리스너 중복 등록 | 낮 | `removeListener` 후 `addListener` 패턴 또는 `initState`에서 1회 등록 |

---

## 검증 결과

- [코드 리뷰 보고서](sprint6/code-review.md)
- [검증 보고서](sprint6/validation-report.md)

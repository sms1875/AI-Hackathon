# Sprint 5 구현 계획 - 자동 태그 + AI 추천 + 검색 API

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 크롤링 파이프라인에 Claude Vision API 기반 자동 태그 생성을 추가하고, 태그 기반 검색 API, 좋아요 기반 AI 추천 API를 구현하며, Flutter 앱에 태그 필터 칩과 추천 섹션을 연결하여 M3(Phase 2 완료) 마일스톤을 달성한다.

**Architecture:** 태그는 Wallpaper.tags JSON 필드에 저장되며, 크롤링 완료 후 비동기로 생성된다. 추천은 UserLike 엔티티에서 device-id별 좋아요 이력을 태그 빈도로 분석하여 유사 배경화면을 반환한다. Flutter 앱은 새 API 엔드포인트를 호출하는 Provider/Repository 확장으로 기존 구조를 최대한 재사용한다.

**Tech Stack:** Spring Boot 3.x (Java 21), Spring Data JPA, SQLite, Claude API (Anthropic), Flutter (Dart), Provider, http 패키지

---

## 검증 결과

- [검증 보고서](sprint5/validation-report.md)
- [코드 리뷰](sprint5/code-review.md)
- [배포 가이드](sprint5/deploy.md)

**브랜치:** `sprint5` (master에서 분기)

---

## 전제 조건 및 현재 상태

- `Wallpaper` 엔티티에 `tags TEXT`, `description TEXT` 컬럼이 이미 존재함 (Sprint 1에서 정의)
- `ClaudeApiClient`가 이미 존재하며 `ANTHROPIC_API_KEY` 미설정 시 데모 모드로 동작
- `GenericCrawlerExecutor.downloadAndSave()`가 이미지 저장 후 `Wallpaper`를 save함
- Flutter `Wallpaper` 모델에는 `tags` 필드가 없음 (이번 Sprint에서 추가)

---

## Task 1: TaggingService — Claude Vision API 기반 태그 자동 생성

**목표:** 이미지 바이트를 받아 Claude Vision API를 호출하고 JSON 태그 배열을 반환하는 서비스 구현

**Files:**
- Create: `server/src/main/java/com/gamepaper/claude/TaggingService.java`
- Modify: `server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java`
- Create: `server/src/test/java/com/gamepaper/claude/TaggingServiceTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/claude/TaggingServiceTest.java`

```java
package com.gamepaper.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaggingServiceTest {

    @Mock
    private ClaudeApiClient claudeApiClient;

    @InjectMocks
    private TaggingService taggingService;

    @Test
    void generateTags_정상응답시_태그목록반환() {
        // given
        when(claudeApiClient.generateTagsFromImage(any(byte[].class), anyString()))
                .thenReturn(List.of("dark", "landscape", "blue-tone"));

        // when
        List<String> tags = taggingService.generateTags(new byte[]{1, 2, 3}, "jpg");

        // then
        assertThat(tags).containsExactly("dark", "landscape", "blue-tone");
    }

    @Test
    void generateTags_예외발생시_빈목록반환() {
        // given
        when(claudeApiClient.generateTagsFromImage(any(byte[].class), anyString()))
                .thenThrow(new RuntimeException("API 에러"));

        // when
        List<String> tags = taggingService.generateTags(new byte[]{1, 2, 3}, "jpg");

        // then
        assertThat(tags).isEmpty();
    }
}
```

### Step 2: 테스트 실행 — FAIL 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.claude.TaggingServiceTest" 2>&1 | tail -20
```

예상: `TaggingService` 클래스가 없으므로 컴파일 에러

### Step 3: ClaudeApiClient에 Vision API 메서드 추가

`server/src/main/java/com/gamepaper/claude/ClaudeApiClient.java` 에 다음 메서드를 추가한다. (기존 `analyzeHtml` 메서드는 유지)

```java
/**
 * 이미지 바이트에서 태그를 생성합니다 (Claude Vision API 사용).
 *
 * @param imageBytes 이미지 바이트 배열
 * @param extension  파일 확장자 (jpg, png, webp)
 * @return 태그 목록 (예: ["dark", "landscape", "character"])
 * @throws IllegalStateException API 키가 설정되지 않은 경우
 */
public List<String> generateTagsFromImage(byte[] imageBytes, String extension) {
    if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalStateException("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
    }

    // 이미지를 Base64로 인코딩
    String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
    String mediaType = resolveMediaType(extension);

    String responseText = callVisionApi(base64Image, mediaType);
    log.debug("태그 생성 응답 수신 - 길이={}", responseText.length());

    return parseTagsFromResponse(responseText);
}

private String resolveMediaType(String extension) {
    return switch (extension.toLowerCase()) {
        case "png" -> "image/png";
        case "webp" -> "image/webp";
        case "gif" -> "image/gif";
        default -> "image/jpeg";
    };
}

private String callVisionApi(String base64Image, String mediaType) {
    String tagPrompt = """
            이 이미지를 분석하여 배경화면 태그를 생성해주세요.
            태그는 영어 소문자로 작성하며, 하이픈(-)으로 단어를 연결합니다.
            예시 태그: dark, bright, landscape, city, character, fantasy, sci-fi, blue-tone, warm-color, minimalist

            JSON 배열 형식으로만 응답하세요. 예: ["dark", "landscape", "blue-tone"]
            태그는 5~10개 사이로 생성하세요.
            """;

    ObjectNode requestBody = MAPPER.createObjectNode();
    requestBody.put("model", model);
    requestBody.put("max_tokens", 256);

    ArrayNode messages = MAPPER.createArrayNode();
    ObjectNode userMessage = MAPPER.createObjectNode();
    userMessage.put("role", "user");

    ArrayNode content = MAPPER.createArrayNode();

    // 이미지 content block
    ObjectNode imageContent = MAPPER.createObjectNode();
    imageContent.put("type", "image");
    ObjectNode imageSource = MAPPER.createObjectNode();
    imageSource.put("type", "base64");
    imageSource.put("media_type", mediaType);
    imageSource.put("data", base64Image);
    imageContent.set("source", imageSource);
    content.add(imageContent);

    // 텍스트 content block
    ObjectNode textContent = MAPPER.createObjectNode();
    textContent.put("type", "text");
    textContent.put("text", tagPrompt);
    content.add(textContent);

    userMessage.set("content", content);
    messages.add(userMessage);
    requestBody.set("messages", messages);

    String responseBody = restClient.post()
            .uri(apiUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .body(requestBody.toString())
            .retrieve()
            .body(String.class);

    try {
        JsonNode root = MAPPER.readTree(responseBody);
        return root.path("content").path(0).path("text").asText();
    } catch (Exception e) {
        throw new RuntimeException("Claude Vision API 응답 파싱 실패: " + e.getMessage(), e);
    }
}

private List<String> parseTagsFromResponse(String responseText) {
    // JSON 배열 추출 (```json ... ``` 감싸진 경우도 처리)
    String cleaned = responseText
            .replaceAll("(?s)```json\\s*", "")
            .replaceAll("(?s)```\\s*", "")
            .trim();

    // [ ... ] 부분만 추출
    int start = cleaned.indexOf('[');
    int end = cleaned.lastIndexOf(']');
    if (start < 0 || end < 0 || start >= end) {
        log.warn("태그 JSON 파싱 실패 - 원본: {}", responseText);
        return java.util.Collections.emptyList();
    }

    try {
        String jsonArray = cleaned.substring(start, end + 1);
        com.fasterxml.jackson.databind.node.ArrayNode arr =
                (com.fasterxml.jackson.databind.node.ArrayNode) MAPPER.readTree(jsonArray);
        List<String> tags = new java.util.ArrayList<>();
        arr.forEach(node -> tags.add(node.asText()));
        return tags;
    } catch (Exception e) {
        log.warn("태그 JSON 파싱 실패: {}", e.getMessage());
        return java.util.Collections.emptyList();
    }
}
```

ClaudeApiClient 임포트 상단에 추가:
```java
import java.util.List;
```

### Step 4: TaggingService 구현

`server/src/main/java/com/gamepaper/claude/TaggingService.java`

```java
package com.gamepaper.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Claude Vision API를 사용하여 이미지에서 태그를 자동 생성하는 서비스.
 *
 * API 키 미설정 또는 예외 발생 시 빈 목록을 반환하여 크롤링 파이프라인을 중단시키지 않는다.
 * 태그 목록을 JSON 배열 문자열로 직렬화하여 Wallpaper.tags 필드에 저장 가능한 형태로 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaggingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeApiClient claudeApiClient;

    /**
     * 이미지에서 태그를 생성합니다.
     *
     * @param imageBytes 이미지 바이트
     * @param extension  파일 확장자 (jpg, png, webp)
     * @return 태그 목록 (실패 시 빈 목록)
     */
    public List<String> generateTags(byte[] imageBytes, String extension) {
        try {
            List<String> tags = claudeApiClient.generateTagsFromImage(imageBytes, extension);
            log.debug("태그 생성 완료 - 태그 수={}", tags.size());
            return tags;
        } catch (IllegalStateException e) {
            log.debug("API 키 미설정 - 태그 생성 건너뜀");
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("태그 생성 실패 (무시): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 태그 목록을 JSON 배열 문자열로 직렬화합니다.
     * Wallpaper.tags 컬럼에 저장할 형태로 변환합니다.
     *
     * @param tags 태그 목록
     * @return JSON 배열 문자열 (예: ["dark","landscape"]) 또는 null (빈 목록)
     */
    public String toJsonString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("태그 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
```

### Step 5: 테스트 실행 — PASS 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.claude.TaggingServiceTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`, 2개 테스트 PASS

### Step 6: 커밋

```bash
cd server
git add src/main/java/com/gamepaper/claude/TaggingService.java \
        src/main/java/com/gamepaper/claude/ClaudeApiClient.java \
        src/test/java/com/gamepaper/claude/TaggingServiceTest.java
git commit -m "feat: TaggingService + ClaudeApiClient Vision API 태그 생성 구현 (Sprint 5 Task 1)"
```

---

## Task 2: 크롤링 파이프라인에 태그 생성 단계 추가 + 배치 태깅

**목표:** GenericCrawlerExecutor의 `downloadAndSave()` 에 태그 생성 단계를 추가하고, 기존 이미지에 일괄 태그를 생성하는 배치 서비스를 구현한다.

**Files:**
- Modify: `server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java`
- Create: `server/src/main/java/com/gamepaper/claude/BatchTaggingService.java`
- Create: `server/src/main/java/com/gamepaper/admin/AdminTaggingApiController.java`
- Create: `server/src/test/java/com/gamepaper/claude/BatchTaggingServiceTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/claude/BatchTaggingServiceTest.java`

```java
package com.gamepaper.claude;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchTaggingServiceTest {

    @Mock
    private WallpaperRepository wallpaperRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private TaggingService taggingService;

    @InjectMocks
    private BatchTaggingService batchTaggingService;

    @Test
    void tagAllUntagged_태그없는배경화면에태그생성() throws Exception {
        // given
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags(null);
        when(wallpaperRepository.findAllByTagsIsNull()).thenReturn(List.of(wp));
        when(storageService.download(anyLong(), anyString())).thenReturn(new byte[]{1, 2, 3});
        when(taggingService.generateTags(any(), anyString())).thenReturn(List.of("dark", "landscape"));
        when(taggingService.toJsonString(anyList())).thenReturn("[\"dark\",\"landscape\"]");

        // when
        int count = batchTaggingService.tagAllUntagged();

        // then
        verify(wallpaperRepository, times(1)).save(wp);
        assert count == 1;
    }

    @Test
    void tagAllUntagged_저장소오류시건너뜀() throws Exception {
        // given
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags(null);
        when(wallpaperRepository.findAllByTagsIsNull()).thenReturn(List.of(wp));
        when(storageService.download(anyLong(), anyString())).thenThrow(new RuntimeException("파일 없음"));

        // when
        int count = batchTaggingService.tagAllUntagged();

        // then
        verify(wallpaperRepository, never()).save(any());
        assert count == 0;
    }
}
```

### Step 2: StorageService에 download 메서드 추가

`server/src/main/java/com/gamepaper/storage/StorageService.java` 에 다음을 추가한다.

```java
/**
 * 저장된 파일을 바이트 배열로 읽습니다.
 *
 * @param gameId   게임 ID
 * @param fileName 파일명
 * @return 파일 바이트 배열
 * @throws java.io.IOException 파일이 없거나 읽기 실패 시
 */
byte[] download(Long gameId, String fileName) throws java.io.IOException;
```

### Step 3: LocalStorageService에 download 구현 추가

`server/src/main/java/com/gamepaper/storage/local/LocalStorageService.java` 에 추가한다.

```java
@Override
public byte[] download(Long gameId, String fileName) throws java.io.IOException {
    java.nio.file.Path filePath = java.nio.file.Paths.get(storageRoot, String.valueOf(gameId), fileName);
    return java.nio.file.Files.readAllBytes(filePath);
}
```

`storageRoot` 필드가 이미 LocalStorageService에 정의되어 있는지 먼저 확인한다. `@Value("${storage.root:/app/storage}")` 로 주입되어 있을 것이다.

### Step 4: WallpaperRepository에 쿼리 메서드 추가

`server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java` 에 추가한다.

```java
// 태그 미생성 배경화면 조회 (배치 태깅용)
List<Wallpaper> findAllByTagsIsNull();
```

임포트 추가:
```java
import java.util.List;
```

### Step 5: BatchTaggingService 구현

`server/src/main/java/com/gamepaper/claude/BatchTaggingService.java`

```java
package com.gamepaper.claude;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 기존 배경화면에 일괄 태그를 생성하는 배치 서비스.
 *
 * Claude Vision API rate limit 고려: 각 처리 사이에 1초 대기.
 * 예외 발생 시 해당 항목을 건너뛰고 계속 진행 (크롤링 파이프라인 영향 최소화).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTaggingService {

    private final WallpaperRepository wallpaperRepository;
    private final StorageService storageService;
    private final TaggingService taggingService;

    /**
     * 태그가 없는 모든 배경화면에 태그를 생성합니다.
     * 동기 실행 - 관리자 API에서 비동기(@Async) 래핑 후 호출됩니다.
     *
     * @return 성공적으로 태그가 생성된 배경화면 수
     */
    public int tagAllUntagged() {
        List<Wallpaper> untagged = wallpaperRepository.findAllByTagsIsNull();
        log.info("배치 태깅 시작 - 대상 수={}", untagged.size());

        int successCount = 0;
        for (Wallpaper wallpaper : untagged) {
            try {
                byte[] imageBytes = storageService.download(wallpaper.getGameId(), wallpaper.getFileName());
                String ext = extractExtension(wallpaper.getFileName());

                List<String> tags = taggingService.generateTags(imageBytes, ext);
                if (!tags.isEmpty()) {
                    wallpaper.setTags(taggingService.toJsonString(tags));
                    wallpaperRepository.save(wallpaper);
                    successCount++;
                    log.debug("태그 생성 완료 - wallpaperId={}, tags={}", wallpaper.getId(), tags);
                }

                // rate limit 방지: 1초 대기
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("배치 태깅 중단됨 - wallpaperId={}", wallpaper.getId());
                break;
            } catch (Exception e) {
                log.warn("태그 생성 건너뜀 - wallpaperId={}, 오류={}", wallpaper.getId(), e.getMessage());
            }
        }

        log.info("배치 태깅 완료 - 성공={}/{}", successCount, untagged.size());
        return successCount;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "jpg";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
```

### Step 6: GenericCrawlerExecutor에 태그 생성 단계 추가

`server/src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java` 수정:

클래스 필드에 `TaggingService` 추가:
```java
private final TaggingService taggingService;
```

`downloadAndSave()` 메서드에서 `wallpaperRepository.save(wallpaper)` 호출 직전에 다음 블록을 추가한다:

```java
// 태그 생성 (비동기 처리 없이 순차 실행 - 이미 크롤러가 별도 스레드에서 실행 중)
try {
    List<String> tags = taggingService.generateTags(imageBytes, ext);
    if (!tags.isEmpty()) {
        wallpaper.setTags(taggingService.toJsonString(tags));
    }
} catch (Exception e) {
    log.debug("태그 생성 건너뜀 (크롤링 계속 진행) - 오류={}", e.getMessage());
}
```

클래스 상단 임포트에 추가:
```java
import com.gamepaper.claude.TaggingService;
import java.util.List;
```

### Step 7: AdminTaggingApiController 구현

`server/src/main/java/com/gamepaper/admin/AdminTaggingApiController.java`

```java
package com.gamepaper.admin;

import com.gamepaper.claude.BatchTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 배치 태깅 관리자 API.
 * POST /admin/api/tagging/batch — 태그 없는 배경화면 전체 태깅 (비동기)
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/tagging")
@RequiredArgsConstructor
public class AdminTaggingApiController {

    private final BatchTaggingService batchTaggingService;

    /**
     * 태그 없는 배경화면 전체에 일괄 태그 생성을 시작합니다 (비동기).
     * 즉시 202 Accepted 반환 후 백그라운드에서 처리됩니다.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> startBatchTagging() {
        log.info("배치 태깅 시작 요청");
        CompletableFuture.runAsync(batchTaggingService::tagAllUntagged);
        return ResponseEntity.accepted()
                .body(Map.of("message", "배치 태깅이 시작되었습니다. 로그를 통해 진행 상황을 확인하세요."));
    }
}
```

### Step 8: 관리자 UI 배경화면 탭에 태그 표시 추가

`server/src/main/resources/templates/admin/game-detail.html` 의 배경화면 탭 부분 수정.

기존 카드 내 `<div class="card-body p-1">` 내용을 다음으로 교체:

```html
<div class="card-body p-1">
  <p class="card-text text-muted mb-1" style="font-size: 0.7rem;"
     th:text="${wp.width != null ? wp.width + 'x' + wp.height : '-'}">해상도</p>
  <div th:if="${wp.tags != null and !#strings.isEmpty(wp.tags)}"
       class="d-flex flex-wrap gap-1 mt-1">
    <!-- tags는 JSON 문자열이므로 Thymeleaf에서 그대로 표시 -->
    <span class="badge bg-secondary" style="font-size: 0.6rem;"
          th:text="${wp.tags}">태그</span>
  </div>
</div>
```

또한 배경화면 탭 상단 (수집된 배경화면이 없습니다 div 위) 에 배치 태깅 버튼을 추가:

```html
<div class="d-flex justify-content-end mb-2">
  <button type="button" class="btn btn-sm btn-outline-info" id="btnBatchTag">
    <i class="bi bi-tags me-1"></i>전체 태그 생성
  </button>
</div>
```

`<script th:inline="javascript">` 블록에 다음 추가:

```javascript
// 배치 태그 생성 버튼
document.getElementById('btnBatchTag')?.addEventListener('click', function() {
  const btn = this;
  btn.disabled = true;
  btn.textContent = '태그 생성 중...';
  fetch('/admin/api/tagging/batch', { method: 'POST' })
    .then(r => r.json())
    .then(data => {
      alert(data.message);
      btn.disabled = false;
      btn.innerHTML = '<i class="bi bi-tags me-1"></i>전체 태그 생성';
    })
    .catch(() => { btn.disabled = false; });
});
```

### Step 9: 테스트 실행 — PASS 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.claude.BatchTaggingServiceTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`, 2개 테스트 PASS

### Step 10: 전체 빌드 확인

```bash
cd server && ./gradlew build 2>&1 | tail -30
```

예상: `BUILD SUCCESSFUL`

### Step 11: 커밋

```bash
cd server
git add src/main/java/com/gamepaper/claude/BatchTaggingService.java \
        src/main/java/com/gamepaper/admin/AdminTaggingApiController.java \
        src/main/java/com/gamepaper/crawler/generic/GenericCrawlerExecutor.java \
        src/main/java/com/gamepaper/storage/StorageService.java \
        src/main/java/com/gamepaper/storage/local/LocalStorageService.java \
        src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java \
        src/main/resources/templates/admin/game-detail.html \
        src/test/java/com/gamepaper/claude/BatchTaggingServiceTest.java
git commit -m "feat: 크롤링 파이프라인 태그 생성 연동 + 배치 태깅 서비스 구현 (Sprint 5 Task 2)"
```

---

## Task 3: 태그 기반 검색 API

**목표:** `GET /api/wallpapers/search?tags=dark,landscape` 와 `GET /api/tags` 엔드포인트 구현

**Files:**
- Modify: `server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`
- Modify: `server/src/main/java/com/gamepaper/api/WallpaperApiController.java`
- Create: `server/src/main/java/com/gamepaper/api/TagApiController.java`
- Create: `server/src/main/java/com/gamepaper/api/dto/TagDto.java`
- Create: `server/src/test/java/com/gamepaper/api/WallpaperSearchApiTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/api/WallpaperSearchApiTest.java`

```java
package com.gamepaper.api;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WallpaperSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @Test
    void search_단일태그_검색() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags("[\"dark\",\"landscape\"]");
        when(wallpaperRepository.findByTagsContainingAll(anyList(), anyInt()))
                .thenReturn(List.of(wp));

        mockMvc.perform(get("/api/wallpapers/search")
                        .param("tags", "dark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tags").value("[\"dark\",\"landscape\"]"));
    }

    @Test
    void search_태그파라미터없음_400반환() throws Exception {
        mockMvc.perform(get("/api/wallpapers/search"))
                .andExpect(status().isBadRequest());
    }
}
```

### Step 2: WallpaperRepository에 태그 검색 쿼리 추가

SQLite는 JSON 함수를 제한적으로 지원하므로, `tags LIKE` 패턴 매칭으로 구현한다.

`server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java` 에 추가:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 태그 검색 - SQLite LIKE 패턴으로 각 태그가 포함된 배경화면 조회.
 * AND 조건: 모든 태그가 포함된 항목만 반환.
 * 최대 limit 개수 반환.
 *
 * 주의: tags 필드는 JSON 배열 문자열 (예: ["dark","landscape"])
 *       태그 검색은 "dark" 문자열 포함 여부로 판단
 */
@Query(value = """
    SELECT * FROM wallpapers w
    WHERE w.tags IS NOT NULL
    AND (:tag1 IS NULL OR w.tags LIKE CONCAT('%"', :tag1, '"%'))
    AND (:tag2 IS NULL OR w.tags LIKE CONCAT('%"', :tag2, '"%'))
    AND (:tag3 IS NULL OR w.tags LIKE CONCAT('%"', :tag3, '"%'))
    ORDER BY w.created_at DESC
    LIMIT :limit
    """, nativeQuery = true)
List<Wallpaper> findByTagsMatchingNative(
    @Param("tag1") String tag1,
    @Param("tag2") String tag2,
    @Param("tag3") String tag3,
    @Param("limit") int limit
);

// 태그 없는 배경화면 조회 (배치 태깅용)
List<Wallpaper> findAllByTagsIsNull();
```

**별도 헬퍼 메서드 (Repository 아님)**: `WallpaperRepository`에는 네이티브 쿼리 메서드를 추가하고, 컨트롤러 레이어에서 최대 3개 태그까지 지원하도록 처리한다.

실제 동적 AND 검색을 위해 다음 패턴을 사용한다. 네이티브 쿼리 대신 Java stream 필터를 적용한다 (SQLite 호환성 최대화):

```java
// 태그 기반 배경화면 검색 (인메모리 필터링)
// SQLite의 JSON 함수 제한으로 인해 tags LIKE '%"tag"%' 패턴을 Java에서 처리
@Query("SELECT w FROM Wallpaper w WHERE w.tags IS NOT NULL ORDER BY w.createdAt DESC")
List<Wallpaper> findAllTagged();
```

### Step 3: WallpaperSearchService 구현

`server/src/main/java/com/gamepaper/api/WallpaperSearchService.java`

```java
package com.gamepaper.api;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 태그 기반 배경화면 검색 서비스.
 *
 * SQLite의 JSON 함수 지원 제한으로 인메모리 필터링 사용.
 * tags 필드는 JSON 배열 문자열 ("dark","landscape") 형태.
 *
 * AND 검색: 모든 쿼리 태그가 포함된 배경화면만 반환.
 * OR 검색: 쿼리 태그 중 하나라도 포함된 배경화면 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WallpaperSearchService {

    private final WallpaperRepository wallpaperRepository;

    private static final int MAX_RESULTS = 50;

    /**
     * 태그 AND 검색
     *
     * @param tags  검색할 태그 목록
     * @return 모든 태그가 포함된 배경화면 목록 (최대 50개)
     */
    public List<Wallpaper> searchByTagsAnd(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();

        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> containsAllTags(wp.getTags(), tags))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * 태그 OR 검색
     *
     * @param tags  검색할 태그 목록
     * @return 태그 중 하나라도 포함된 배경화면 목록 (최대 50개)
     */
    public List<Wallpaper> searchByTagsOr(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();

        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> containsAnyTag(wp.getTags(), tags))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * 전체 태그 빈도 분석.
     * tags JSON 배열 필드를 파싱하여 태그별 출현 횟수를 계산한다.
     *
     * @return 태그명 → 출현 횟수 맵 (출현 횟수 내림차순 정렬)
     */
    public java.util.Map<String, Long> getTagFrequency() {
        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> wp.getTags() != null)
                .flatMap(wp -> parseTagsFromJson(wp.getTags()).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        java.util.Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }

    private boolean containsAllTags(String tagsJson, List<String> queryTags) {
        if (tagsJson == null) return false;
        List<String> wallpaperTags = parseTagsFromJson(tagsJson);
        return queryTags.stream().allMatch(wallpaperTags::contains);
    }

    private boolean containsAnyTag(String tagsJson, List<String> queryTags) {
        if (tagsJson == null) return false;
        List<String> wallpaperTags = parseTagsFromJson(tagsJson);
        return queryTags.stream().anyMatch(wallpaperTags::contains);
    }

    /**
     * JSON 배열 문자열에서 태그 목록을 파싱합니다.
     * 예: ["dark","landscape","blue-tone"] → ["dark", "landscape", "blue-tone"]
     */
    public List<String> parseTagsFromJson(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            // 간단한 JSON 배열 파싱 (Jackson 사용)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] arr = mapper.readValue(tagsJson, String[].class);
            return Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("태그 JSON 파싱 실패: {}", tagsJson);
            return Collections.emptyList();
        }
    }
}
```

### Step 4: WallpaperRepository에 `findAllTagged` 추가 (실제 구현)

`server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`:

```java
import org.springframework.data.jpa.repository.Query;

@Query("SELECT w FROM Wallpaper w WHERE w.tags IS NOT NULL ORDER BY w.createdAt DESC")
List<Wallpaper> findAllTagged();
```

### Step 5: WallpaperApiController에 search 엔드포인트 추가

`server/src/main/java/com/gamepaper/api/WallpaperApiController.java` 에 추가:

```java
private final WallpaperSearchService searchService;
```

```java
/**
 * 태그 기반 배경화면 검색.
 *
 * @param tags  쉼표로 구분된 태그 목록 (예: dark,landscape)
 * @param mode  검색 방식 - "and" (기본값, 모든 태그 포함) 또는 "or" (하나라도 포함)
 */
@GetMapping("/search")
public List<WallpaperDto> searchByTags(
        @RequestParam(required = false) String tags,
        @RequestParam(defaultValue = "and") String mode) {

    if (tags == null || tags.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tags 파라미터가 필요합니다.");
    }

    List<String> tagList = Arrays.stream(tags.split(","))
            .map(String::trim)
            .filter(t -> !t.isBlank())
            .collect(Collectors.toList());

    List<Wallpaper> results = "or".equalsIgnoreCase(mode)
            ? searchService.searchByTagsOr(tagList)
            : searchService.searchByTagsAnd(tagList);

    return results.stream().map(WallpaperDto::new).collect(Collectors.toList());
}
```

임포트 추가:
```java
import com.gamepaper.domain.wallpaper.Wallpaper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
```

### Step 6: TagApiController 구현

`server/src/main/java/com/gamepaper/api/TagApiController.java`

```java
package com.gamepaper.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 태그 관련 API 컨트롤러.
 * GET /api/tags — 사용 중인 태그 목록 (빈도순)
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagApiController {

    private final WallpaperSearchService searchService;

    /**
     * 사용 중인 태그 목록을 빈도 내림차순으로 반환합니다.
     *
     * 응답 예시:
     * [
     *   {"tag": "dark", "count": 42},
     *   {"tag": "landscape", "count": 38}
     * ]
     */
    @GetMapping
    public List<Map<String, Object>> getTags() {
        Map<String, Long> frequency = searchService.getTagFrequency();
        return frequency.entrySet().stream()
                .map(e -> Map.of("tag", (Object) e.getKey(), "count", (Object) e.getValue()))
                .collect(Collectors.toList());
    }
}
```

### Step 7: 테스트 실행 — PASS 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.WallpaperSearchApiTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`

### Step 8: 전체 빌드 확인

```bash
cd server && ./gradlew build 2>&1 | tail -30
```

### Step 9: 커밋

```bash
cd server
git add src/main/java/com/gamepaper/api/WallpaperApiController.java \
        src/main/java/com/gamepaper/api/TagApiController.java \
        src/main/java/com/gamepaper/api/WallpaperSearchService.java \
        src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java \
        src/test/java/com/gamepaper/api/WallpaperSearchApiTest.java
git commit -m "feat: 태그 기반 검색 API (GET /api/wallpapers/search, GET /api/tags) 구현 (Sprint 5 Task 3)"
```

---

## Task 4: UserLike 엔티티 + 좋아요 API

**목표:** `UserLike` JPA 엔티티와 `user_likes` DB 테이블을 생성하고, `POST /api/wallpapers/{id}/like` 엔드포인트를 구현한다.

**Files:**
- Create: `server/src/main/java/com/gamepaper/domain/like/UserLike.java`
- Create: `server/src/main/java/com/gamepaper/domain/like/UserLikeRepository.java`
- Modify: `server/src/main/java/com/gamepaper/api/WallpaperApiController.java`
- Create: `server/src/test/java/com/gamepaper/api/LikeApiTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/api/LikeApiTest.java`

```java
package com.gamepaper.api;

import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LikeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserLikeRepository userLikeRepository;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @Test
    void like_새로운좋아요_liked반환() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        when(wallpaperRepository.findById(1L)).thenReturn(Optional.of(wp));
        when(userLikeRepository.existsByDeviceIdAndWallpaperId("device-001", 1L)).thenReturn(false);

        mockMvc.perform(post("/api/wallpapers/1/like")
                        .header("device-id", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));

        verify(userLikeRepository, times(1)).save(any(UserLike.class));
    }

    @Test
    void like_이미좋아요한경우_unliked반환() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        when(wallpaperRepository.findById(1L)).thenReturn(Optional.of(wp));
        when(userLikeRepository.existsByDeviceIdAndWallpaperId("device-001", 1L)).thenReturn(true);

        mockMvc.perform(post("/api/wallpapers/1/like")
                        .header("device-id", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));

        verify(userLikeRepository, times(1)).deleteByDeviceIdAndWallpaperId("device-001", 1L);
    }

    @Test
    void like_deviceId헤더없음_400반환() throws Exception {
        mockMvc.perform(post("/api/wallpapers/1/like"))
                .andExpect(status().isBadRequest());
    }
}
```

### Step 2: UserLike 엔티티 구현

`server/src/main/java/com/gamepaper/domain/like/UserLike.java`

```java
package com.gamepaper.domain.like;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 좋아요 엔티티.
 *
 * 사용자 식별: device-id 헤더 (기기 고유 ID, 앱 재설치 시 초기화됨).
 * 서버에 최소 정보만 저장: deviceId + wallpaperId + 시각.
 *
 * 복합 유니크 제약: (deviceId, wallpaperId) — 중복 좋아요 방지.
 */
@Entity
@Table(name = "user_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "wallpaper_id"}))
@Getter
@NoArgsConstructor
public class UserLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "wallpaper_id", nullable = false)
    private Long wallpaperId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public UserLike(String deviceId, Long wallpaperId) {
        this.deviceId = deviceId;
        this.wallpaperId = wallpaperId;
    }
}
```

### Step 3: UserLikeRepository 구현

`server/src/main/java/com/gamepaper/domain/like/UserLikeRepository.java`

```java
package com.gamepaper.domain.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    boolean existsByDeviceIdAndWallpaperId(String deviceId, Long wallpaperId);

    void deleteByDeviceIdAndWallpaperId(String deviceId, Long wallpaperId);

    List<UserLike> findAllByDeviceId(String deviceId);

    long countByWallpaperId(Long wallpaperId);

    /**
     * 특정 기기의 좋아요에서 가장 많이 등장한 태그를 반환합니다.
     * wallpaper의 tags 필드(JSON 배열)와 join하여 집계합니다.
     *
     * 실제 구현은 WallpaperRepository.findByIds()로 배경화면 조회 후
     * Java에서 태그 빈도 분석으로 처리합니다.
     */
    @Query("SELECT ul.wallpaperId FROM UserLike ul WHERE ul.deviceId = :deviceId ORDER BY ul.createdAt DESC")
    List<Long> findWallpaperIdsByDeviceId(@Param("deviceId") String deviceId);
}
```

### Step 4: WallpaperApiController에 like 엔드포인트 추가

`server/src/main/java/com/gamepaper/api/WallpaperApiController.java` 에 다음을 추가한다.

클래스 필드에 추가:
```java
private final UserLikeRepository userLikeRepository;
```

엔드포인트 메서드 추가:
```java
/**
 * 좋아요 토글 API.
 * device-id 헤더로 사용자를 식별합니다.
 *
 * @param id       배경화면 ID
 * @param deviceId 기기 ID (device-id 헤더)
 * @return {"liked": true/false, "likeCount": N}
 */
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
```

임포트 추가:
```java
import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import java.util.Map;
```

### Step 5: 테스트 실행 — PASS 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.LikeApiTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`, 3개 테스트 PASS

### Step 6: 커밋

```bash
cd server
git add src/main/java/com/gamepaper/domain/like/ \
        src/main/java/com/gamepaper/api/WallpaperApiController.java \
        src/test/java/com/gamepaper/api/LikeApiTest.java
git commit -m "feat: UserLike 엔티티 + 좋아요 토글 API (POST /api/wallpapers/{id}/like) 구현 (Sprint 5 Task 4)"
```

---

## Task 5: AI 추천 API

**목표:** 좋아요 이력을 기반으로 선호 태그를 분석하고 유사 배경화면을 추천하는 `GET /api/wallpapers/recommended` 엔드포인트를 구현한다.

**Files:**
- Create: `server/src/main/java/com/gamepaper/api/RecommendationService.java`
- Modify: `server/src/main/java/com/gamepaper/api/WallpaperApiController.java`
- Create: `server/src/test/java/com/gamepaper/api/RecommendationServiceTest.java`

### Step 1: 테스트 작성

`server/src/test/java/com/gamepaper/api/RecommendationServiceTest.java`

```java
package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void recommend_좋아요이력없으면빈목록() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-001"))
                .thenReturn(List.of());

        List<Wallpaper> result = recommendationService.recommend("device-001");

        assertThat(result).isEmpty();
    }

    @Test
    void recommend_좋아요이력있으면유사배경화면반환() {
        // 좋아요한 배경화면 ID 목록
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-001"))
                .thenReturn(List.of(1L, 2L));

        // 좋아요한 배경화면들
        Wallpaper liked1 = new Wallpaper(1L, "a.jpg", "http://example.com/a.jpg");
        liked1.setTags("[\"dark\",\"landscape\"]");
        Wallpaper liked2 = new Wallpaper(2L, "b.jpg", "http://example.com/b.jpg");
        liked2.setTags("[\"dark\",\"character\"]");

        when(wallpaperRepository.findAllById(anyList())).thenReturn(List.of(liked1, liked2));

        // 검색 결과 (dark 태그 기반 OR 검색)
        Wallpaper recommended = new Wallpaper(3L, "c.jpg", "http://example.com/c.jpg");
        recommended.setTags("[\"dark\",\"fantasy\"]");
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(recommended));

        when(searchService.parseTagsFromJson("[\"dark\",\"landscape\"]"))
                .thenReturn(List.of("dark", "landscape"));
        when(searchService.parseTagsFromJson("[\"dark\",\"character\"]"))
                .thenReturn(List.of("dark", "character"));

        List<Wallpaper> result = recommendationService.recommend("device-001");

        assertThat(result).isNotEmpty();
        assertThat(result).doesNotContain(liked1, liked2); // 이미 좋아요한 항목 제외
    }
}
```

### Step 2: RecommendationService 구현

`server/src/main/java/com/gamepaper/api/RecommendationService.java`

```java
package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 좋아요 기반 배경화면 추천 서비스.
 *
 * 추천 로직:
 * 1. device-id의 좋아요 이력에서 wallpaperId 목록 조회
 * 2. 해당 배경화면들의 tags를 파싱하여 태그 빈도 분석
 * 3. 상위 빈도 태그로 OR 검색 → 유사 배경화면 후보 조회
 * 4. 이미 좋아요한 배경화면 제외 후 최대 20개 반환
 *
 * 응답 시간 3초 이내 목표: 인메모리 필터링으로 DB 쿼리 최소화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int MAX_PREFERRED_TAGS = 5;
    private static final int MAX_RECOMMENDATIONS = 20;

    private final UserLikeRepository userLikeRepository;
    private final WallpaperRepository wallpaperRepository;
    private final WallpaperSearchService searchService;

    /**
     * 사용자의 좋아요 이력을 기반으로 추천 배경화면을 반환합니다.
     *
     * @param deviceId 기기 ID
     * @return 추천 배경화면 목록 (최대 20개, 좋아요 이력 없으면 빈 목록)
     */
    public List<Wallpaper> recommend(String deviceId) {
        // 1. 좋아요한 배경화면 ID 조회
        List<Long> likedIds = userLikeRepository.findWallpaperIdsByDeviceId(deviceId);
        if (likedIds.isEmpty()) {
            log.debug("좋아요 이력 없음 - deviceId={}", deviceId);
            return Collections.emptyList();
        }

        Set<Long> likedIdSet = new HashSet<>(likedIds);

        // 2. 좋아요한 배경화면의 태그 빈도 분석
        List<Wallpaper> likedWallpapers = wallpaperRepository.findAllById(likedIds);
        Map<String, Long> tagFrequency = likedWallpapers.stream()
                .filter(wp -> wp.getTags() != null)
                .flatMap(wp -> searchService.parseTagsFromJson(wp.getTags()).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        if (tagFrequency.isEmpty()) {
            log.debug("좋아요한 배경화면에 태그 없음 - deviceId={}", deviceId);
            return Collections.emptyList();
        }

        // 3. 상위 빈도 태그 선택
        List<String> preferredTags = tagFrequency.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(MAX_PREFERRED_TAGS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.debug("선호 태그 - deviceId={}, tags={}", deviceId, preferredTags);

        // 4. OR 검색으로 후보 배경화면 조회
        List<Wallpaper> candidates = searchService.searchByTagsOr(preferredTags);

        // 5. 이미 좋아요한 항목 제외 후 최대 20개 반환
        return candidates.stream()
                .filter(wp -> !likedIdSet.contains(wp.getId()))
                .limit(MAX_RECOMMENDATIONS)
                .collect(Collectors.toList());
    }
}
```

### Step 3: WallpaperApiController에 recommended 엔드포인트 추가

```java
private final RecommendationService recommendationService;
```

```java
/**
 * 추천 배경화면 API.
 * device-id 기반으로 좋아요 이력을 분석하여 유사 배경화면을 반환합니다.
 *
 * @param deviceId 기기 ID (device-id 헤더)
 * @return 추천 배경화면 목록 (좋아요 이력 없으면 빈 배열)
 */
@GetMapping("/recommended")
public List<WallpaperDto> getRecommended(
        @RequestHeader(value = "device-id", required = false) String deviceId) {

    if (deviceId == null || deviceId.isBlank()) {
        return List.of(); // device-id 없으면 빈 목록 반환
    }

    List<Wallpaper> recommendations = recommendationService.recommend(deviceId);
    return recommendations.stream().map(WallpaperDto::new).collect(Collectors.toList());
}
```

### Step 4: 테스트 실행 — PASS 확인

```bash
cd server && ./gradlew test --tests "com.gamepaper.api.RecommendationServiceTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`

### Step 5: 전체 테스트 실행

```bash
cd server && ./gradlew test 2>&1 | tail -30
```

예상: 전체 테스트 PASS

### Step 6: 커밋

```bash
cd server
git add src/main/java/com/gamepaper/api/RecommendationService.java \
        src/main/java/com/gamepaper/api/WallpaperApiController.java \
        src/test/java/com/gamepaper/api/RecommendationServiceTest.java
git commit -m "feat: AI 추천 서비스 + GET /api/wallpapers/recommended 구현 (Sprint 5 Task 5)"
```

---

## Task 6: Flutter 앱 — Wallpaper 모델 태그 필드 추가 + API 연결

**목표:** Flutter `Wallpaper` 모델에 `tags`, `likeCount` 필드를 추가하고, `GameRepository`에 검색 API와 좋아요 API 메서드를 추가한다.

**Files:**
- Modify: `/d/work/GamePaper/client/lib/models/game.dart`
- Modify: `/d/work/GamePaper/client/lib/config/api_config.dart`
- Modify: `/d/work/GamePaper/client/lib/repositories/game_repository.dart`

### Step 1: Wallpaper 모델에 tags, likeCount 필드 추가

`/d/work/GamePaper/client/lib/models/game.dart` 의 `Wallpaper` 클래스를 수정한다.

```dart
class Wallpaper {
  final int id;
  final String url;
  final String? blurHash;
  final int width;
  final int height;
  final List<String> tags;      // 추가
  final int likeCount;          // 추가

  Wallpaper({
    required this.id,
    required this.url,
    this.blurHash,
    this.width = 0,
    this.height = 0,
    this.tags = const [],       // 추가
    this.likeCount = 0,         // 추가
  });

  factory Wallpaper.fromServerJson(Map<String, dynamic> json) {
    // tags 파싱: 서버는 JSON 배열 문자열("[\"dark\",\"landscape\"]")로 전달
    List<String> parsedTags = [];
    final rawTags = json['tags'];
    if (rawTags != null && rawTags is String && rawTags.isNotEmpty) {
      try {
        import 'dart:convert';
        final decoded = jsonDecode(rawTags);
        if (decoded is List) {
          parsedTags = decoded.cast<String>();
        }
      } catch (_) {}
    }

    return Wallpaper(
      id: json['id'] as int? ?? 0,
      url: json['url'] as String,
      blurHash: json['blurHash'] as String?,
      width: json['width'] as int? ?? 0,
      height: json['height'] as int? ?? 0,
      tags: parsedTags,
      likeCount: json['likeCount'] as int? ?? 0,
    );
  }
}
```

**주의:** dart에서 `import 'dart:convert'`는 파일 상단에서 임포트해야 한다. 실제 구현 시 파일 상단에 다음을 추가한다:

```dart
import 'dart:convert';
```

### Step 2: ApiConfig에 새 URL 추가

`/d/work/GamePaper/client/lib/config/api_config.dart` 수정:

```dart
import 'dart:convert';

/// 서버 API 기본 URL 설정
class ApiConfig {
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static String gamesUrl() => '$baseUrl/api/games';

  static String wallpapersUrl(int gameId, {int page = 0, int size = 12}) =>
      '$baseUrl/api/wallpapers/$gameId?page=$page&size=$size';

  // 태그 기반 검색
  static String searchUrl({required String tags, String mode = 'and'}) =>
      '$baseUrl/api/wallpapers/search?tags=${Uri.encodeComponent(tags)}&mode=$mode';

  // 태그 목록
  static String tagsUrl() => '$baseUrl/api/tags';

  // 추천 배경화면
  static String recommendedUrl() => '$baseUrl/api/wallpapers/recommended';

  // 좋아요 토글
  static String likeUrl(int wallpaperId) =>
      '$baseUrl/api/wallpapers/$wallpaperId/like';
}
```

### Step 3: GameRepository에 API 메서드 추가

`/d/work/GamePaper/client/lib/repositories/game_repository.dart` 에 다음 메서드들을 추가한다:

```dart
/// 태그 목록 조회 (빈도순)
Future<List<Map<String, dynamic>>> fetchTags() async {
  final response = await http
      .get(
        Uri.parse(ApiConfig.tagsUrl()),
        headers: {'Content-Type': 'application/json'},
      )
      .timeout(const Duration(seconds: 10));

  if (response.statusCode == 200) {
    final List<dynamic> data = json.decode(response.body);
    return data.cast<Map<String, dynamic>>();
  } else {
    throw Exception('태그 목록 조회 실패: ${response.statusCode}');
  }
}

/// 태그 기반 검색
Future<List<Wallpaper>> searchByTags(List<String> tags, {String mode = 'and'}) async {
  final tagsParam = tags.join(',');
  final response = await http
      .get(
        Uri.parse(ApiConfig.searchUrl(tags: tagsParam, mode: mode)),
        headers: {'Content-Type': 'application/json'},
      )
      .timeout(const Duration(seconds: 10));

  if (response.statusCode == 200) {
    final List<dynamic> data = json.decode(response.body);
    return data.map((json) => Wallpaper.fromServerJson(json)).toList();
  } else {
    throw Exception('태그 검색 실패: ${response.statusCode}');
  }
}

/// 추천 배경화면 조회
Future<List<Wallpaper>> fetchRecommended(String deviceId) async {
  final response = await http
      .get(
        Uri.parse(ApiConfig.recommendedUrl()),
        headers: {
          'Content-Type': 'application/json',
          'device-id': deviceId,
        },
      )
      .timeout(const Duration(seconds: 10));

  if (response.statusCode == 200) {
    final List<dynamic> data = json.decode(response.body);
    return data.map((json) => Wallpaper.fromServerJson(json)).toList();
  } else {
    throw Exception('추천 배경화면 조회 실패: ${response.statusCode}');
  }
}

/// 좋아요 토글
/// 반환: {'liked': bool, 'likeCount': int}
Future<Map<String, dynamic>> toggleLike(int wallpaperId, String deviceId) async {
  final response = await http
      .post(
        Uri.parse(ApiConfig.likeUrl(wallpaperId)),
        headers: {
          'Content-Type': 'application/json',
          'device-id': deviceId,
        },
      )
      .timeout(const Duration(seconds: 10));

  if (response.statusCode == 200) {
    return json.decode(response.body) as Map<String, dynamic>;
  } else {
    throw Exception('좋아요 토글 실패: ${response.statusCode}');
  }
}
```

### Step 4: 커밋

```bash
cd /d/work/GamePaper/client
git add lib/models/game.dart lib/config/api_config.dart lib/repositories/game_repository.dart
git commit -m "feat: Wallpaper 모델 tags/likeCount 추가 + 검색/추천/좋아요 API 메서드 추가 (Sprint 5 Task 6)"
```

---

## Task 7: Flutter 앱 — 태그 필터 칩 + 추천 섹션 + 좋아요 버튼

**목표:** WallpaperScreen 상단에 태그 필터 칩을 추가하고, HomeScreen에 추천 배경화면 섹션을 추가하며, WallpaperCard에 좋아요 버튼을 추가한다.

**Files:**
- Create: `/d/work/GamePaper/client/lib/providers/tag_filter_provider.dart`
- Create: `/d/work/GamePaper/client/lib/providers/recommendation_provider.dart`
- Create: `/d/work/GamePaper/client/lib/widgets/wallpaper/tag_filter_chips.dart`
- Create: `/d/work/GamePaper/client/lib/widgets/home/recommended_section.dart`
- Modify: `/d/work/GamePaper/client/lib/widgets/wallpaper/wallpaper_card.dart`
- Modify: `/d/work/GamePaper/client/lib/screens/wallpaper_screen.dart`
- Modify: `/d/work/GamePaper/client/lib/screens/home_screen.dart`
- Modify: `/d/work/GamePaper/client/lib/main.dart`

### Step 1: device-id 유틸리티

`/d/work/GamePaper/client/lib/utils/device_id.dart`

```dart
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:math';

/// 기기 고유 ID를 생성하고 영구 저장하는 유틸리티.
/// 앱 재설치 시 초기화됩니다.
class DeviceId {
  static const _key = 'device_id';
  static String? _cached;

  /// 기기 ID를 반환합니다. 최초 호출 시 생성하여 SharedPreferences에 저장합니다.
  static Future<String> get() async {
    if (_cached != null) return _cached!;

    final prefs = await SharedPreferences.getInstance();
    String? id = prefs.getString(_key);
    if (id == null) {
      id = _generate();
      await prefs.setString(_key, id);
    }
    _cached = id;
    return id;
  }

  static String _generate() {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    final random = Random.secure();
    return List.generate(16, (_) => chars[random.nextInt(chars.length)]).join();
  }
}
```

pubspec.yaml에 `shared_preferences` 의존성 추가:

```yaml
dependencies:
  shared_preferences: ^2.2.0
```

### Step 2: TagFilterProvider 구현

`/d/work/GamePaper/client/lib/providers/tag_filter_provider.dart`

```dart
import 'package:flutter/foundation.dart';
import '../repositories/game_repository.dart';
import '../models/game.dart';

/// 태그 필터 상태를 관리하는 Provider.
/// 선택된 태그 목록과 필터링된 배경화면 결과를 관리합니다.
class TagFilterProvider with ChangeNotifier {
  final GameRepository _repository;

  List<Map<String, dynamic>> _allTags = [];
  Set<String> _selectedTags = {};
  List<Wallpaper> _filteredWallpapers = [];
  bool _isLoading = false;
  bool _isFilterActive = false;

  TagFilterProvider({required GameRepository repository})
      : _repository = repository;

  List<Map<String, dynamic>> get allTags => _allTags;
  Set<String> get selectedTags => _selectedTags;
  List<Wallpaper> get filteredWallpapers => _filteredWallpapers;
  bool get isLoading => _isLoading;
  bool get isFilterActive => _isFilterActive;

  /// 태그 목록을 서버에서 로드합니다.
  Future<void> loadTags() async {
    try {
      _allTags = await _repository.fetchTags();
      notifyListeners();
    } catch (e) {
      debugPrint('태그 목록 로드 실패: $e');
    }
  }

  /// 태그 선택/해제 토글. 선택 후 자동으로 검색합니다.
  Future<void> toggleTag(String tag) async {
    if (_selectedTags.contains(tag)) {
      _selectedTags.remove(tag);
    } else {
      _selectedTags.add(tag);
    }
    notifyListeners();

    if (_selectedTags.isEmpty) {
      _isFilterActive = false;
      _filteredWallpapers = [];
      notifyListeners();
      return;
    }

    _isFilterActive = true;
    _isLoading = true;
    notifyListeners();

    try {
      _filteredWallpapers = await _repository.searchByTags(
        _selectedTags.toList(),
        mode: 'and',
      );
    } catch (e) {
      debugPrint('태그 검색 실패: $e');
      _filteredWallpapers = [];
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void clearFilter() {
    _selectedTags.clear();
    _filteredWallpapers = [];
    _isFilterActive = false;
    notifyListeners();
  }
}
```

### Step 3: RecommendationProvider 구현

`/d/work/GamePaper/client/lib/providers/recommendation_provider.dart`

```dart
import 'package:flutter/foundation.dart';
import '../repositories/game_repository.dart';
import '../models/game.dart';
import '../utils/device_id.dart';

/// 추천 배경화면 상태를 관리하는 Provider.
class RecommendationProvider with ChangeNotifier {
  final GameRepository _repository;

  List<Wallpaper> _recommendations = [];
  bool _isLoading = false;
  bool _hasLikeHistory = false;

  RecommendationProvider({required GameRepository repository})
      : _repository = repository;

  List<Wallpaper> get recommendations => _recommendations;
  bool get isLoading => _isLoading;
  bool get hasLikeHistory => _hasLikeHistory;

  /// 추천 배경화면을 로드합니다.
  Future<void> loadRecommendations() async {
    _isLoading = true;
    notifyListeners();

    try {
      final deviceId = await DeviceId.get();
      _recommendations = await _repository.fetchRecommended(deviceId);
      _hasLikeHistory = _recommendations.isNotEmpty;
    } catch (e) {
      debugPrint('추천 배경화면 로드 실패: $e');
      _recommendations = [];
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
```

### Step 4: TagFilterChips 위젯 구현

`/d/work/GamePaper/client/lib/widgets/wallpaper/tag_filter_chips.dart`

```dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/tag_filter_provider.dart';

/// 배경화면 그리드 상단에 표시되는 태그 필터 칩 위젯.
/// 태그 선택 시 서버 검색 API를 호출하여 결과를 필터링합니다.
class TagFilterChips extends StatelessWidget {
  const TagFilterChips({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<TagFilterProvider>(
      builder: (context, provider, _) {
        if (provider.allTags.isEmpty) return const SizedBox.shrink();

        return Column(
          children: [
            SizedBox(
              height: 40,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                itemCount: provider.allTags.length,
                separatorBuilder: (_, __) => const SizedBox(width: 6),
                itemBuilder: (context, index) {
                  final tagData = provider.allTags[index];
                  final tag = tagData['tag'] as String;
                  final isSelected = provider.selectedTags.contains(tag);

                  return FilterChip(
                    label: Text(
                      tag,
                      style: TextStyle(
                        fontSize: 11,
                        color: isSelected ? Colors.white : Colors.grey[300],
                      ),
                    ),
                    selected: isSelected,
                    onSelected: (_) => provider.toggleTag(tag),
                    backgroundColor: Colors.grey[800],
                    selectedColor: Colors.blue[700],
                    checkmarkColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  );
                },
              ),
            ),
            if (provider.selectedTags.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4, right: 8),
                child: Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: provider.clearFilter,
                    child: const Text('필터 초기화', style: TextStyle(fontSize: 11)),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
```

### Step 5: RecommendedSection 위젯 구현

`/d/work/GamePaper/client/lib/widgets/home/recommended_section.dart`

```dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/recommendation_provider.dart';
import '../../models/game.dart';
import '../wallpaper/wallpaper_dialog.dart';
import '../common/load_network_image.dart';

/// 홈 화면의 추천 배경화면 섹션.
/// 좋아요 이력이 있을 때만 표시됩니다.
class RecommendedSection extends StatefulWidget {
  const RecommendedSection({super.key});

  @override
  State<RecommendedSection> createState() => _RecommendedSectionState();
}

class _RecommendedSectionState extends State<RecommendedSection> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Provider.of<RecommendationProvider>(context, listen: false)
          .loadRecommendations();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<RecommendationProvider>(
      builder: (context, provider, _) {
        if (provider.isLoading) {
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          );
        }

        if (!provider.hasLikeHistory || provider.recommendations.isEmpty) {
          return const SizedBox.shrink();
        }

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Row(
                children: [
                  Icon(Icons.auto_awesome, color: Colors.amber, size: 16),
                  SizedBox(width: 6),
                  Text(
                    '추천 배경화면',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(
              height: 120,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: provider.recommendations.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final wallpaper = provider.recommendations[index];
                  return _RecommendedCard(wallpaper: wallpaper);
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        );
      },
    );
  }
}

class _RecommendedCard extends StatelessWidget {
  final Wallpaper wallpaper;

  const _RecommendedCard({required this.wallpaper});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => showDialog(
        context: context,
        builder: (_) => WallpaperDialog(imageUrl: wallpaper.url),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox(
          width: 70,
          height: 120,
          child: loadNetworkImage(
            wallpaper.url,
            blurHash: wallpaper.blurHash,
            fit: BoxFit.cover,
            errorWidget: const Icon(Icons.error, color: Colors.grey),
          ),
        ),
      ),
    );
  }
}
```

### Step 6: WallpaperCard에 좋아요 버튼 추가

`/d/work/GamePaper/client/lib/widgets/wallpaper/wallpaper_card.dart` 수정:

```dart
import 'package:flutter/material.dart';
import 'package:gamepaper/widgets/wallpaper/wallpaper_dialog.dart';
import 'package:gamepaper/widgets/common/load_network_image.dart';
import 'package:gamepaper/models/game.dart';
import 'package:gamepaper/repositories/game_repository.dart';
import 'package:gamepaper/utils/device_id.dart';

/// 배경화면을 카드 형태로 표시하는 위젯.
/// 이미지를 탭하면 전체 화면 다이얼로그를 표시하고,
/// 좋아요 버튼으로 서버 API를 호출합니다.
class WallpaperCard extends StatefulWidget {
  final Wallpaper wallpaper;

  const WallpaperCard({super.key, required this.wallpaper});

  @override
  State<WallpaperCard> createState() => _WallpaperCardState();
}

class _WallpaperCardState extends State<WallpaperCard> {
  late bool _isLiked;
  late int _likeCount;
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    _isLiked = false; // 초기 상태: 서버에서 조회하지 않고 로컬 상태만 관리
    _likeCount = widget.wallpaper.likeCount;
  }

  Future<void> _toggleLike() async {
    if (_isProcessing) return;
    setState(() => _isProcessing = true);

    try {
      final deviceId = await DeviceId.get();
      final result = await GameRepository().toggleLike(widget.wallpaper.id, deviceId);
      setState(() {
        _isLiked = result['liked'] as bool;
        _likeCount = result['likeCount'] as int;
      });
    } catch (e) {
      debugPrint('좋아요 토글 실패: $e');
    } finally {
      setState(() => _isProcessing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.black,
      margin: const EdgeInsets.all(1),
      child: Stack(
        children: [
          GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () => showDialog(
              context: context,
              builder: (_) => WallpaperDialog(imageUrl: widget.wallpaper.url),
            ),
            child: loadNetworkImage(
              widget.wallpaper.url,
              blurHash: widget.wallpaper.blurHash,
              fit: BoxFit.cover,
              errorWidget: const Icon(Icons.error),
            ),
          ),
          // 좋아요 버튼 (카드 우하단)
          Positioned(
            bottom: 4,
            right: 4,
            child: GestureDetector(
              onTap: _toggleLike,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.6),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _isProcessing
                        ? const SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(
                              strokeWidth: 1.5,
                              color: Colors.white,
                            ),
                          )
                        : Icon(
                            _isLiked ? Icons.favorite : Icons.favorite_border,
                            color: _isLiked ? Colors.red : Colors.white,
                            size: 12,
                          ),
                    const SizedBox(width: 3),
                    Text(
                      '$_likeCount',
                      style: const TextStyle(color: Colors.white, fontSize: 10),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

### Step 7: WallpaperScreen에 TagFilterChips 통합

`/d/work/GamePaper/client/lib/screens/wallpaper_screen.dart` 수정:

`build()` 메서드에서 `ChangeNotifierProvider` 를 다중 Provider로 교체:

```dart
import 'package:gamepaper/providers/tag_filter_provider.dart';
import 'package:gamepaper/widgets/wallpaper/tag_filter_chips.dart';

// ChangeNotifierProvider → MultiProvider로 변경
return MultiProvider(
  providers: [
    ChangeNotifierProvider(
      create: (_) => WallpaperProvider(game: widget.game, repository: GameRepository()),
    ),
    ChangeNotifierProvider(
      create: (_) => TagFilterProvider(repository: GameRepository())..loadTags(),
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
                  const Expanded(child: Center(child: CircularProgressIndicator()))
                else if (tagProvider.filteredWallpapers.isEmpty)
                  const Expanded(
                    child: Center(
                      child: Text('검색 결과가 없습니다.', style: TextStyle(color: Colors.grey)),
                    ),
                  )
                else
                  Expanded(
                    child: GridView.builder(
                      padding: const EdgeInsets.all(8),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 3,
                        crossAxisSpacing: 8,
                        mainAxisSpacing: 8,
                        childAspectRatio: 9 / 16,
                      ),
                      itemCount: tagProvider.filteredWallpapers.length,
                      itemBuilder: (_, i) => WallpaperCard(
                        wallpaper: tagProvider.filteredWallpapers[i],
                      ),
                    ),
                  ),
              ],
            );
          }

          // 일반 뷰 (페이지네이션 그리드)
          return Column(
            children: [
              const TagFilterChips(),
              Expanded(
                child: FutureBuilder<int>(
                  future: wallpaperProvider.totalWallpapersFuture,
                  builder: (context, snapshot) {
                    // 기존 로직 그대로 유지
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Center(child: LoadingWidget());
                    } else if (snapshot.hasError) {
                      return ErrorDisplayWidget(
                        error: snapshot.error!,
                        onRetry: () => wallpaperProvider.loadWallpapers(),
                      );
                    } else if (!snapshot.hasData || snapshot.data == 0) {
                      return ErrorDisplayWidget(
                        error: 'no-wallpapers-available',
                        onRetry: () => wallpaperProvider.loadWallpapers(),
                      );
                    }

                    final totalWallpapers = snapshot.data!;
                    final pageCount =
                        (totalWallpapers / wallpaperProvider.wallpapersPerPage).ceil();

                    return Column(
                      children: [
                        _buildPageView(pageCount, wallpaperProvider),
                        _buildSmoothPageIndicator(pageCount),
                      ],
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    ),
  ),
);
```

임포트 추가:
```dart
import 'package:gamepaper/widgets/wallpaper/wallpaper_card.dart';
import 'package:gamepaper/providers/tag_filter_provider.dart';
import 'package:gamepaper/widgets/wallpaper/tag_filter_chips.dart';
```

### Step 8: HomeScreen에 RecommendedSection 추가

`/d/work/GamePaper/client/lib/screens/home_screen.dart` 수정:

`build()` 에서 `Scaffold` 래핑 Provider 추가, `ListView.builder` 상단에 `RecommendedSection` 삽입:

```dart
import 'package:gamepaper/providers/recommendation_provider.dart';
import 'package:gamepaper/widgets/home/recommended_section.dart';

// initState에서 Provider 초기화 후 ChangeNotifierProvider 주입
return ChangeNotifierProvider(
  create: (_) => RecommendationProvider(repository: GameRepository()),
  child: Scaffold(
    backgroundColor: Colors.grey[800],
    body: Consumer<HomeProvider>(
      builder: (context, homeProvider, child) {
        // ... 기존 로딩/에러 처리 유지 ...

        final gameMap = homeProvider.gameMap;
        return ListView.builder(
          // 항목 수 +1: 0번 인덱스에 RecommendedSection 삽입
          itemCount: gameMap.length + 1,
          itemBuilder: (BuildContext context, int index) {
            if (index == 0) {
              return const RecommendedSection();
            }

            final adjustedIndex = index - 1;
            final String alphabet = gameMap.keys.elementAt(adjustedIndex);
            final List<Game> gamesByAlphabet = gameMap[alphabet]!;

            return AlphabetGameSection(
              alphabet: alphabet,
              games: gamesByAlphabet,
              isSelected: selectedAlphabets.contains(alphabet),
              onAlphabetTap: () {
                setState(() {
                  if (selectedAlphabets.contains(alphabet)) {
                    selectedAlphabets.remove(alphabet);
                  } else {
                    selectedAlphabets.add(alphabet);
                  }
                });
              },
            );
          },
        );
      },
    ),
  ),
);
```

### Step 9: main.dart에서 RecommendationProvider 등록

`/d/work/GamePaper/client/lib/main.dart` 의 `MultiProvider` 또는 최상위 Provider 설정에 `RecommendationProvider` 를 추가한다. main.dart를 먼저 확인하여 Provider 등록 방식에 맞게 추가한다.

```dart
ChangeNotifierProvider(
  create: (_) => RecommendationProvider(repository: GameRepository()),
),
```

### Step 10: 빌드 확인

```bash
cd /d/work/GamePaper/client
flutter pub get
flutter analyze 2>&1 | tail -30
```

예상: `No issues found!` 또는 경고만 있고 에러 없음

### Step 11: 커밋

```bash
cd /d/work/GamePaper/client
git add lib/providers/tag_filter_provider.dart \
        lib/providers/recommendation_provider.dart \
        lib/widgets/wallpaper/tag_filter_chips.dart \
        lib/widgets/home/recommended_section.dart \
        lib/widgets/wallpaper/wallpaper_card.dart \
        lib/screens/wallpaper_screen.dart \
        lib/screens/home_screen.dart \
        lib/utils/device_id.dart \
        lib/config/api_config.dart \
        pubspec.yaml
git commit -m "feat: Flutter 태그 필터 칩 + 추천 배경화면 섹션 + 좋아요 버튼 구현 (Sprint 5 Task 7)"
```

---

## Task 8: 전체 빌드 검증 + deploy.md

**목표:** 서버 전체 테스트 실행, Flutter analyze, deploy.md 작성으로 Sprint 5를 마무리한다.

**Files:**
- Modify: `docs/ROADMAP.md` (Sprint 5 상태 업데이트)
- Create: `docs/sprint/sprint5/deploy.md`

### Step 1: 서버 전체 테스트 실행

```bash
cd server && ./gradlew test 2>&1 | tail -40
```

예상: `BUILD SUCCESSFUL`, 전체 테스트 PASS

### Step 2: Flutter analyze

```bash
cd /d/work/GamePaper/client && flutter analyze 2>&1 | tail -20
```

예상: `No issues found!`

### Step 3: deploy.md 작성

`docs/sprint/sprint5/deploy.md` 에 다음 내용을 작성한다.

```markdown
# Sprint 5 배포 가이드

## 자동 검증 완료

- ✅ 서버 전체 테스트 PASS (TaggingServiceTest, BatchTaggingServiceTest, WallpaperSearchApiTest, LikeApiTest, RecommendationServiceTest)
- ✅ Flutter analyze 에러 없음

## 수동 검증 필요

### 1. 서버 재빌드 (신규 엔티티 user_likes 테이블 생성 포함)

```bash
docker compose up --build
```

JPA auto-ddl=update 설정에 의해 `user_likes` 테이블이 자동 생성됩니다.

### 2. API 동작 확인

```bash
# 태그 목록 (기존 이미지에 태그가 있는 경우)
curl http://localhost:8080/api/tags

# 태그 기반 검색 (dark 태그 포함 배경화면)
curl "http://localhost:8080/api/wallpapers/search?tags=dark"

# 추천 API (좋아요 이력 없으면 빈 배열)
curl -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/recommended

# 좋아요 토글
curl -X POST -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/1/like
```

### 3. 기존 이미지 배치 태깅 실행

서버 기동 후 관리자 UI에서 배치 태깅을 실행하거나 API를 직접 호출합니다:

```bash
curl -X POST http://localhost:8080/admin/api/tagging/batch
```

- Claude API 키가 설정된 경우: 실제 태그 생성
- API 키 미설정 시: 태그 생성 건너뜀 (빈 목록 반환)

### 4. Flutter 앱 실행 확인

```bash
cd /d/work/GamePaper/client
flutter run
```

확인 항목:
- ⬜ 배경화면 화면 상단에 태그 필터 칩이 표시된다
- ⬜ 태그 선택 시 필터링된 배경화면이 표시된다
- ⬜ 배경화면 카드 우하단에 좋아요 버튼(하트 아이콘 + 카운트)이 표시된다
- ⬜ 좋아요 버튼 탭 시 하트 아이콘이 빨간색으로 변경되고 카운트가 증가한다
- ⬜ 홈 화면에서 좋아요 이력이 있는 경우 "추천 배경화면" 섹션이 표시된다

### 5. 관리자 UI 태그 표시 확인

브라우저에서 `http://localhost:8080/admin/games/{id}` 접속:
- ⬜ 배경화면 탭에서 각 이미지 하단에 태그가 표시된다 (배치 태깅 실행 후)
- ⬜ "전체 태그 생성" 버튼 클릭 시 202 Accepted 응답과 함께 백그라운드 처리가 시작된다
```

### Step 4: 커밋

```bash
git add docs/sprint/sprint5/deploy.md
git commit -m "docs: Sprint 5 deploy.md 작성 (Task 8)"
```

---

## 완료 기준 (Definition of Done)

| 항목 | 검증 방법 |
|------|----------|
| 크롤링 시 이미지에 태그 자동 생성 | docker compose up 후 크롤링 실행 → DB wallpapers.tags 확인 |
| 태그 기반 검색 API 동작 | `curl /api/wallpapers/search?tags=dark` → 200 응답 |
| GET /api/tags 응답 | `curl /api/tags` → [{tag, count}] 배열 반환 |
| 좋아요 API 토글 | `curl -X POST /api/wallpapers/1/like` → liked 필드 토글 확인 |
| 추천 API 응답 | 좋아요 후 `curl /api/wallpapers/recommended` → 배경화면 목록 반환 |
| Flutter 태그 필터 칩 표시 | 앱 실행 → WallpaperScreen 상단 칩 확인 |
| Flutter 추천 섹션 표시 | 좋아요 후 홈 화면 진입 → 추천 섹션 확인 |
| Flutter 좋아요 버튼 | 배경화면 카드 우하단 하트 아이콘 탭 → 상태 변경 확인 |

---

## 주요 기술 결정 사항

| 결정 | 선택 | 이유 |
|------|------|------|
| 태그 저장 형식 | Wallpaper.tags JSON 문자열 | 스키마 변경 없이 기존 컬럼 활용 |
| 태그 검색 방식 | 인메모리 LIKE 필터링 | SQLite JSON 함수 제한, 데이터 규모 상 성능 충분 |
| 태그 생성 시점 | 크롤링 파이프라인 내 동기 처리 | 크롤러가 이미 별도 스레드에서 실행, 추가 비동기 불필요 |
| 사용자 식별 | device-id 헤더 | 서버에 최소 정보만 저장, 계정 시스템 불필요 |
| 추천 알고리즘 | 태그 빈도 기반 OR 검색 | 초기 단순 구현 (YAGNI), 데이터 축적 후 고도화 |

---

## 스프린트 기간

2026-03-15 시작 | 목표 완료: 2026-03-22

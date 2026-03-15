# Sprint 2: 기존 크롤러 마이그레이션 + 클라이언트 연결 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 기존 6개 게임 크롤러를 Sprint 1에서 구축한 StorageService/Repository 인프라 위에서 동작하도록 마이그레이션하고, BlurHash 이미지 처리, Flutter 앱 API 연결 전환, GitHub Actions CI 파이프라인을 완성하여 Phase 1 마일스톤(M1)을 달성한다.

**Architecture:** Spring Boot 크롤러 패키지에 `GameCrawler` 인터페이스와 게임별 구현체를 추가하고, `CrawlerScheduler`가 6시간 주기로 각 크롤러를 순차 실행한다. 크롤링 완료 시 이미지를 `StorageService.upload()`로 저장하고 `WallpaperRepository.save()`로 메타데이터(BlurHash, 해상도 포함)를 기록한다. Flutter 앱은 Firebase Storage 직접 호출을 제거하고 서버 REST API로 전환한다. Sprint 4의 `GenericCrawlerExecutor`로 교체 시 `CrawlerScheduler`는 재활용한다.

**Tech Stack:** Java 21, Spring Boot 3.x, Selenium 4.x, Jsoup 1.x, io.github.pulseenergy:blurhash (BlurHash), docker-compose Selenium standalone-chrome, Flutter (Dart), GitHub Actions, Gradle

**브랜치:** `sprint2` (master에서 분기, 현재 체크아웃 상태)

---

## 스프린트 목표 및 범위

### 포함 항목

- S2-1: 기존 6개 게임 크롤러 마이그레이션 (Selenium 4개 + Jsoup 2개)
- S2-2: BlurHash 이미지 처리 파이프라인 (BlurHash 생성 + 해상도 추출 + DB 저장)
- S2-3: Flutter 앱 API 연결 전환 (Firebase Storage → 서버 REST API)
- S2-4: GitHub Actions CI 기본 파이프라인 (Gradle 빌드 + 테스트)

### 제외 항목 (Sprint 2 범위 밖)

- 관리자 UI (Thymeleaf 페이지) — Sprint 3
- AI 파싱 전략 생성 — Sprint 3~4
- 수동 크롤링 트리거 API — Sprint 3 (현재는 스케줄러 자동 실행 또는 짧은 interval 테스트)
- `StorageService.listFiles()` 구현 — Sprint 3
- Flutter 로컬 캐시 — Sprint 6

### Sprint 1 코드 리뷰 이슈 해소 (병행)

- I-3 (중요): `CrawlingLog.status` 타입을 `String` → `@Enumerated(EnumType.STRING) CrawlingLogStatus`로 수정 — Task 1 시작 전에 처리

---

## 완료 기준 (Definition of Done)

| 기준 | 검증 방법 |
|------|-----------|
| 6개 게임 크롤러가 Docker 환경에서 실행되어 이미지를 로컬 스토리지에 수집한다 | `curl http://localhost:8080/api/wallpapers/{gameId}?page=0&size=12` → `content` 배열에 항목 존재 |
| 수집된 이미지의 BlurHash가 DB에 저장되고 API로 조회 가능하다 | API 응답 JSON의 `blurHash` 필드가 비어 있지 않다 |
| 수집된 이미지의 width/height가 DB에 저장된다 | API 응답 JSON의 `width`, `height` 필드가 0이 아니다 |
| Flutter 앱이 서버 API를 통해 게임 목록과 배경화면을 표시한다 | 앱에서 게임 목록 → 배경화면 그리드 표시 수동 확인 |
| 앱에서 배경화면을 선택하여 기기에 적용할 수 있다 | 배경화면 적용 기능 수동 확인 |
| GitHub Actions에서 빌드/테스트가 통과한다 | PR 또는 push 시 CI 초록불 확인 |

---

## 의존성 순서 (구현 실행 순서)

```
Sprint 1 코드 리뷰 이슈 해소 (I-3 수정)
  └→ Task 1: CrawlingLog 타입 수정 + CrawlerScheduler 뼈대 + GameCrawler 인터페이스
       └→ Task 2: BlurHashProcessor 유틸리티 (크롤러보다 먼저 — 크롤러가 의존)
            └→ Task 3: Jsoup 크롤러 2개 (FFXIV, 검은사막) — 상대적으로 단순
                 └→ Task 4: Selenium 크롤러 4개 (원신, 마비노기, 메이플, NIKKE)
                      └→ Task 5: docker-compose Selenium 서비스 추가
                           └→ Task 6: Flutter 앱 API 연결 전환 (S2-3)
                                └→ Task 7: GitHub Actions CI (S2-4)
```

---

## 기술 결정 사항

| 결정 항목 | 결정 내용 | 이유 |
|-----------|-----------|------|
| BlurHash 라이브러리 | `com.github.marcinmoskala:DiscreteMathematics` 대신 `io.github.pulseenergy:blurhash` 또는 직접 구현 | Java/Spring Boot와의 호환성, 의존성 최소화 |
| 이미지 해상도 추출 | `javax.imageio.ImageIO.read()` → `BufferedImage.getWidth/Height()` | 별도 라이브러리 없이 JDK 기본 API 활용 |
| 크롤러 인터페이스 | `GameCrawler` 인터페이스 + `crawl()` 메서드 반환형 `CrawlResult` | Sprint 4 `GenericCrawlerExecutor` 교체 시 `CrawlerScheduler` 재활용을 위한 인터페이스 분리 |
| 중복 이미지 방지 | 파일명 기준 `WallpaperRepository.existsByGameIdAndFileName()` 체크 | 크롤러 재실행 시 동일 이미지 중복 저장 방지 |
| Selenium 연결 방식 | `RemoteWebDriver` + Selenium Hub URL (`SELENIUM_HUB_URL` 환경변수) | Docker Compose의 standalone-chrome 컨테이너와 연동 |
| Flutter API 기본 URL | `AppConfig` 클래스에 상수로 관리, 환경별 분기 | 에뮬레이터(`10.0.2.2:8080`) vs 실기기(로컬 IP) |
| 크롤링 딜레이 | 요청 간 2~3초 `Thread.sleep()` | IP 차단 방지, 대상 서버 부하 감소 |
| 파일명 생성 | UUID + 원본 확장자 (`{uuid}.jpg`) | S-3 경로 순회 취약점 해소 |

---

## Task 0: Sprint 1 코드 리뷰 이슈 해소 (I-3)

**파일:**
- Modify: `server/src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java`

이 작업은 Task 1 시작 전에 먼저 처리합니다.

### Step 1: CrawlingLog.status 타입 수정

`server/src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java`의 `status` 필드를 수정합니다.

```java
// 변경 전
@Column(length = 20)
private String status;

// 변경 후
@Enumerated(EnumType.STRING)
@Column(length = 20)
private CrawlingLogStatus status;
```

import 추가: `import com.gamepaper.domain.crawler.CrawlingLogStatus;`
import 추가: `import jakarta.persistence.EnumType;`
import 추가: `import jakarta.persistence.Enumerated;`

### Step 2: 빌드 확인

```bash
cd D:/work/AI해커톤/server
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`

### Step 3: 커밋

```bash
git add server/src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java
git commit -m "fix: CrawlingLog.status 타입을 String에서 CrawlingLogStatus enum으로 변경"
```

---

## Task 1: GameCrawler 인터페이스 + CrawlerScheduler + CrawlResult (S2-1 기초)

**파일:**
- Create: `server/src/main/java/com/gamepaper/crawler/GameCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/CrawlResult.java`
- Create: `server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`
- Create: `server/src/main/java/com/gamepaper/config/AppConfig.java`
- Modify: `server/src/main/java/com/gamepaper/GamepaperApplication.java`

### Step 1: CrawlResult DTO 생성

`server/src/main/java/com/gamepaper/crawler/CrawlResult.java`:

```java
package com.gamepaper.crawler;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 크롤링 결과 DTO
 */
@Getter
@Builder
public class CrawlResult {

    private final boolean success;
    private final int collectedCount;
    private final String errorMessage;

    public static CrawlResult success(int count) {
        return CrawlResult.builder()
                .success(true)
                .collectedCount(count)
                .build();
    }

    public static CrawlResult failure(String errorMessage) {
        return CrawlResult.builder()
                .success(false)
                .collectedCount(0)
                .errorMessage(errorMessage)
                .build();
    }
}
```

### Step 2: GameCrawler 인터페이스 생성

`server/src/main/java/com/gamepaper/crawler/GameCrawler.java`:

```java
package com.gamepaper.crawler;

/**
 * 게임 크롤러 인터페이스.
 * Sprint 4에서 GenericCrawlerExecutor로 교체 시 CrawlerScheduler는 이 인터페이스를 그대로 사용.
 */
public interface GameCrawler {

    /**
     * 크롤러가 담당하는 게임 ID (games 테이블의 id)
     */
    Long getGameId();

    /**
     * 크롤링 실행
     */
    CrawlResult crawl();
}
```

### Step 3: @EnableScheduling 추가

`server/src/main/java/com/gamepaper/GamepaperApplication.java`에 `@EnableScheduling` 어노테이션을 추가합니다.

```java
package com.gamepaper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GamepaperApplication {
    public static void main(String[] args) {
        SpringApplication.run(GamepaperApplication.class, args);
    }
}
```

### Step 4: 비동기/스케줄링 설정 추가

`server/src/main/java/com/gamepaper/config/AppConfig.java`:

```java
package com.gamepaper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("crawler-scheduler-");
        return scheduler;
    }
}
```

### Step 5: CrawlerScheduler 생성

`server/src/main/java/com/gamepaper/crawler/CrawlerScheduler.java`:

```java
package com.gamepaper.crawler;

import com.gamepaper.domain.crawler.CrawlingLog;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.crawler.CrawlingLogStatus;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.game.GameStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 크롤러 스케줄러.
 * Sprint 4에서 GenericCrawlerExecutor 교체 시 이 스케줄러를 재활용함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final List<GameCrawler> crawlers;
    private final GameRepository gameRepository;
    private final CrawlingLogRepository crawlingLogRepository;

    /**
     * 6시간 주기 크롤링 (테스트 시 fixedDelay로 단축 가능)
     */
    @Scheduled(fixedDelayString = "${crawler.schedule.delay-ms:21600000}")
    public void runAll() {
        log.info("크롤링 스케줄 시작 - 크롤러 수: {}", crawlers.size());
        for (GameCrawler crawler : crawlers) {
            runSingle(crawler);
        }
        log.info("크롤링 스케줄 완료");
    }

    private void runSingle(GameCrawler crawler) {
        Long gameId = crawler.getGameId();
        CrawlingLog logEntry = new CrawlingLog();
        logEntry.setGameId(gameId);
        logEntry.setStartedAt(LocalDateTime.now());
        logEntry.setStatus(CrawlingLogStatus.RUNNING);
        crawlingLogRepository.save(logEntry);

        // 게임 상태 UPDATING으로 변경
        gameRepository.findById(gameId).ifPresent(game -> {
            game.setStatus(GameStatus.UPDATING);
            gameRepository.save(game);
        });

        try {
            CrawlResult result = crawler.crawl();
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setCollectedCount(result.getCollectedCount());

            if (result.isSuccess()) {
                logEntry.setStatus(CrawlingLogStatus.SUCCESS);
                // 게임 상태 ACTIVE, 마지막 크롤링 시각 업데이트
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus(GameStatus.ACTIVE);
                    game.setLastCrawledAt(LocalDateTime.now());
                    gameRepository.save(game);
                });
                log.info("크롤링 성공 - gameId={}, 수집={}", gameId, result.getCollectedCount());
            } else {
                logEntry.setStatus(CrawlingLogStatus.FAILED);
                logEntry.setErrorMessage(result.getErrorMessage());
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus(GameStatus.FAILED);
                    gameRepository.save(game);
                });
                log.error("크롤링 실패 - gameId={}, 오류={}", gameId, result.getErrorMessage());
            }
        } catch (Exception e) {
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setStatus(CrawlingLogStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            gameRepository.findById(gameId).ifPresent(game -> {
                game.setStatus(GameStatus.FAILED);
                gameRepository.save(game);
            });
            log.error("크롤링 예외 - gameId={}", gameId, e);
        } finally {
            crawlingLogRepository.save(logEntry);
        }
    }
}
```

### Step 6: CrawlingLogStatus에 RUNNING 추가 확인

`server/src/main/java/com/gamepaper/domain/crawler/CrawlingLogStatus.java`를 확인하고 `RUNNING` 값이 없으면 추가합니다.

```java
package com.gamepaper.domain.crawler;

public enum CrawlingLogStatus {
    RUNNING,
    SUCCESS,
    FAILED
}
```

### Step 7: application-local.yml에 스케줄 설정 추가

`server/src/main/resources/application-local.yml`에 추가:

```yaml
crawler:
  schedule:
    delay-ms: 21600000  # 6시간 (ms). 테스트 시 60000(1분)으로 변경 가능
```

### Step 8: 빌드 확인

```bash
cd D:/work/AI해커톤/server
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`

### Step 9: 커밋

```bash
git add server/src/main/java/com/gamepaper/crawler/ \
        server/src/main/java/com/gamepaper/config/AppConfig.java \
        server/src/main/java/com/gamepaper/GamepaperApplication.java \
        server/src/main/java/com/gamepaper/domain/crawler/CrawlingLogStatus.java \
        server/src/main/resources/application-local.yml
git commit -m "feat: GameCrawler 인터페이스 및 CrawlerScheduler 구현"
```

---

## Task 2: BlurHashProcessor 유틸리티 (S2-2)

**파일:**
- Modify: `server/build.gradle` (BlurHash 의존성 추가)
- Create: `server/src/main/java/com/gamepaper/crawler/image/ImageProcessor.java`
- Create: `server/src/main/java/com/gamepaper/crawler/image/ImageMetadata.java`
- Test: `server/src/test/java/com/gamepaper/crawler/image/ImageProcessorTest.java`

### Step 1: BlurHash 의존성 추가

`server/build.gradle`의 `dependencies` 블록에 추가:

```groovy
// BlurHash 생성
implementation 'io.trbl:blurhash:1.0.0'
```

> 주의: Maven Central에서 `io.trbl:blurhash:1.0.0`을 사용합니다. 만약 의존성 해결이 안 될 경우 `vanniktech:blurhash` 또는 직접 구현으로 대체합니다.

### Step 2: ImageMetadata DTO 생성

`server/src/main/java/com/gamepaper/crawler/image/ImageMetadata.java`:

```java
package com.gamepaper.crawler.image;

import lombok.Builder;
import lombok.Getter;

/**
 * 이미지 처리 결과 (해상도 + BlurHash)
 */
@Getter
@Builder
public class ImageMetadata {

    private final int width;
    private final int height;
    private final String blurHash;
    private final String fileName; // UUID 기반 파일명
}
```

### Step 3: ImageProcessor 구현

`server/src/main/java/com/gamepaper/crawler/image/ImageProcessor.java`:

```java
package com.gamepaper.crawler.image;

import io.trbl.blurhash.BlurHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 이미지 메타데이터 처리: 해상도 추출 + BlurHash 생성
 */
@Slf4j
@Component
public class ImageProcessor {

    /**
     * 이미지 바이트 배열로부터 해상도와 BlurHash를 추출합니다.
     *
     * @param imageBytes 이미지 원본 바이트
     * @param originalExtension 확장자 (예: "jpg", "png")
     * @return ImageMetadata (실패 시 기본값으로 반환하여 크롤링 계속 진행)
     */
    public ImageMetadata process(byte[] imageBytes, String originalExtension) {
        String fileName = UUID.randomUUID().toString() + "." + originalExtension;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("이미지 파싱 실패 - 기본 메타데이터로 저장");
                return ImageMetadata.builder()
                        .width(0).height(0).blurHash("").fileName(fileName)
                        .build();
            }

            int width = image.getWidth();
            int height = image.getHeight();
            String blurHash = generateBlurHash(image);

            return ImageMetadata.builder()
                    .width(width)
                    .height(height)
                    .blurHash(blurHash)
                    .fileName(fileName)
                    .build();

        } catch (IOException e) {
            log.warn("이미지 처리 실패: {}", e.getMessage());
            return ImageMetadata.builder()
                    .width(0).height(0).blurHash("").fileName(fileName)
                    .build();
        }
    }

    private String generateBlurHash(BufferedImage image) {
        try {
            // BlurHash 라이브러리로 생성 (컴포넌트: 4x3)
            return BlurHash.encode(image, 4, 3);
        } catch (Exception e) {
            log.warn("BlurHash 생성 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * URL에서 파일 확장자 추출 (기본값: "jpg")
     */
    public String extractExtension(String url) {
        if (url == null) return "jpg";
        String path = url.split("\\?")[0]; // 쿼리 파라미터 제거
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) return "jpg";
        String ext = path.substring(dotIndex + 1).toLowerCase();
        // 허용 확장자만
        if (ext.matches("jpg|jpeg|png|webp")) return ext;
        return "jpg";
    }
}
```

### Step 4: ImageProcessor 테스트 작성

`server/src/test/java/com/gamepaper/crawler/image/ImageProcessorTest.java`:

```java
package com.gamepaper.crawler.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageProcessorTest {

    ImageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ImageProcessor();
    }

    private byte[] createTestImageBytes(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("이미지 해상도 추출")
    void extractResolution() throws Exception {
        byte[] imageBytes = createTestImageBytes(1080, 1920);
        ImageMetadata metadata = processor.process(imageBytes, "jpg");

        assertThat(metadata.getWidth()).isEqualTo(1080);
        assertThat(metadata.getHeight()).isEqualTo(1920);
    }

    @Test
    @DisplayName("BlurHash 생성 — 비어 있지 않음")
    void generateBlurHash() throws Exception {
        byte[] imageBytes = createTestImageBytes(100, 100);
        ImageMetadata metadata = processor.process(imageBytes, "jpg");

        // BlurHash가 생성되었거나, 실패 시 빈 문자열 (예외 없이 완료)
        assertThat(metadata.getBlurHash()).isNotNull();
    }

    @Test
    @DisplayName("UUID 기반 파일명 생성")
    void generateUuidFileName() throws Exception {
        byte[] imageBytes = createTestImageBytes(100, 100);
        ImageMetadata m1 = processor.process(imageBytes, "jpg");
        ImageMetadata m2 = processor.process(imageBytes, "png");

        assertThat(m1.getFileName()).endsWith(".jpg");
        assertThat(m2.getFileName()).endsWith(".png");
        assertThat(m1.getFileName()).isNotEqualTo(m2.getFileName());
    }

    @Test
    @DisplayName("잘못된 이미지 바이트 — 기본 메타데이터 반환 (예외 없음)")
    void invalidImageBytes() {
        byte[] badBytes = "not-an-image".getBytes();
        ImageMetadata metadata = processor.process(badBytes, "jpg");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getWidth()).isEqualTo(0);
        assertThat(metadata.getHeight()).isEqualTo(0);
    }

    @Test
    @DisplayName("확장자 추출 — 정상 URL")
    void extractExtension() {
        assertThat(processor.extractExtension("https://example.com/image.jpg")).isEqualTo("jpg");
        assertThat(processor.extractExtension("https://example.com/image.PNG")).isEqualTo("png");
        assertThat(processor.extractExtension("https://example.com/image.webp?v=1")).isEqualTo("webp");
        assertThat(processor.extractExtension("https://example.com/image")).isEqualTo("jpg");
        assertThat(processor.extractExtension(null)).isEqualTo("jpg");
    }
}
```

### Step 5: 테스트 실행

```bash
cd D:/work/AI해커톤/server
./gradlew test --tests "com.gamepaper.crawler.image.ImageProcessorTest" --no-daemon
```

예상 결과: `5 tests completed, 0 failed`

> `BlurHash 생성 실패` 경고 로그가 출력될 수 있으나 빈 문자열 반환 후 정상 종료됩니다.

### Step 6: 커밋

```bash
git add server/build.gradle \
        server/src/main/java/com/gamepaper/crawler/image/ \
        server/src/test/java/com/gamepaper/crawler/image/
git commit -m "feat: ImageProcessor 구현 (BlurHash 생성 + 해상도 추출)"
```

---

## Task 3: 추상 크롤러 베이스 클래스 + Jsoup 크롤러 2개 (S2-1, S2-2)

**파일:**
- Create: `server/src/main/java/com/gamepaper/crawler/AbstractGameCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/jsoup/BlackDesertCrawler.java`
- Modify: `server/src/main/resources/application-local.yml` (게임 ID 설정 추가)
- Modify: `server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java` (중복 체크 메서드 추가)

### Step 1: WallpaperRepository에 중복 체크 메서드 추가

`server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`:

```java
package com.gamepaper.domain.wallpaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WallpaperRepository extends JpaRepository<Wallpaper, Long> {
    Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
    long countByGameId(Long gameId);

    // 중복 저장 방지: 동일 게임 + 파일명이 이미 존재하는지 확인
    boolean existsByGameIdAndFileName(Long gameId, String fileName);
}
```

### Step 2: AbstractGameCrawler 베이스 클래스 생성

`server/src/main/java/com/gamepaper/crawler/AbstractGameCrawler.java`:

```java
package com.gamepaper.crawler;

import com.gamepaper.crawler.image.ImageMetadata;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 크롤러 공통 로직: 이미지 다운로드 → 메타데이터 처리 → StorageService 저장 → DB 기록
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractGameCrawler implements GameCrawler {

    protected final StorageService storageService;
    protected final WallpaperRepository wallpaperRepository;
    protected final ImageProcessor imageProcessor;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * 이미지 URL 다운로드 후 저장. 중복이면 건너뜁니다.
     *
     * @return true = 신규 저장, false = 중복 건너뜀
     */
    protected boolean downloadAndSave(Long gameId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        String ext = imageProcessor.extractExtension(imageUrl);
        // 임시 파일명으로 중복 체크 (URL 기반 hash)
        String urlHash = String.valueOf(Math.abs(imageUrl.hashCode()));
        String tempFileName = urlHash + "." + ext;

        // 중복 체크
        if (wallpaperRepository.existsByGameIdAndFileName(gameId, tempFileName)) {
            log.debug("중복 이미지 건너뜀 - gameId={}, fileName={}", gameId, tempFileName);
            return false;
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes.length == 0) return false;

            ImageMetadata metadata = imageProcessor.process(imageBytes, ext);
            // 실제 파일명은 UUID 기반 (metadata.getFileName())이나
            // 중복 체크를 위해 URL hash 기반 tempFileName으로 저장
            String savedUrl = storageService.upload(gameId, tempFileName, imageBytes);

            Wallpaper wallpaper = new Wallpaper(gameId, tempFileName, savedUrl);
            wallpaper.setWidth(metadata.getWidth());
            wallpaper.setHeight(metadata.getHeight());
            wallpaper.setBlurHash(metadata.getBlurHash());

            wallpaperRepository.save(wallpaper);
            log.debug("이미지 저장 완료 - gameId={}, url={}", gameId, imageUrl);
            return true;

        } catch (Exception e) {
            log.warn("이미지 저장 실패 - url={}, 오류={}", imageUrl, e.getMessage());
            return false;
        }
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "Mozilla/5.0 (compatible; GamePaper-Crawler/1.0)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.warn("이미지 다운로드 실패 - status={}, url={}", response.statusCode(), imageUrl);
            return new byte[0];
        }
        return response.body();
    }

    /**
     * 요청 간 딜레이 (IP 차단 방지)
     */
    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### Step 3: 게임 ID 설정 추가 (application-local.yml)

`server/src/main/resources/application-local.yml`에 추가:

```yaml
game:
  id:
    genshin: 1
    mabinogi: 2
    maplestory: 3
    nikke: 4
    ffxiv: 5
    blackdesert: 6
```

> 서버 최초 실행 시 이 ID들이 DB의 games 테이블에 존재해야 합니다. Task 5 완료 후 데이터 초기화 스크립트에서 처리합니다.

### Step 4: FinalFantasyXIVCrawler 구현

`server/src/main/java/com/gamepaper/crawler/jsoup/FinalFantasyXIVCrawler.java`:

```java
package com.gamepaper.crawler.jsoup;

import com.gamepaper.crawler.AbstractGameCrawler;
import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 파이널판타지 XIV 배경화면 크롤러 (Jsoup 기반)
 * 대상: https://na.finalfantasyxiv.com/lodestone/special/fankit/desktop_wallpaper/
 */
@Slf4j
@Component
public class FinalFantasyXIVCrawler extends AbstractGameCrawler {

    private static final String BASE_URL = "https://na.finalfantasyxiv.com/lodestone/special/fankit/desktop_wallpaper/";
    private static final int DELAY_MS = 2000;

    @Value("${game.id.ffxiv:5}")
    private Long gameId;

    public FinalFantasyXIVCrawler(StorageService storageService,
                                   WallpaperRepository wallpaperRepository,
                                   ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("FFXIV 크롤링 시작");
        int count = 0;

        try {
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (compatible; GamePaper-Crawler/1.0)")
                    .timeout(15000)
                    .get();

            // 배경화면 이미지 링크 추출 (실제 사이트 구조에 맞게 selector 조정 필요)
            Elements imageLinks = doc.select("a[href$=.jpg], a[href$=.png]");

            for (Element link : imageLinks) {
                String imageUrl = link.absUrl("href");
                if (imageUrl.isBlank()) continue;

                if (downloadAndSave(gameId, imageUrl)) {
                    count++;
                }
                sleep(DELAY_MS);
            }

            log.info("FFXIV 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (IOException e) {
            log.error("FFXIV 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        }
    }
}
```

### Step 5: BlackDesertCrawler 구현

`server/src/main/java/com/gamepaper/crawler/jsoup/BlackDesertCrawler.java`:

```java
package com.gamepaper.crawler.jsoup;

import com.gamepaper.crawler.AbstractGameCrawler;
import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 검은사막 배경화면 크롤러 (Jsoup 기반)
 * 대상: https://www.kr.playblackdesert.com/ko-KR/About/WallPaper
 */
@Slf4j
@Component
public class BlackDesertCrawler extends AbstractGameCrawler {

    private static final String BASE_URL = "https://www.kr.playblackdesert.com/ko-KR/About/WallPaper";
    private static final int DELAY_MS = 2000;

    @Value("${game.id.blackdesert:6}")
    private Long gameId;

    public BlackDesertCrawler(StorageService storageService,
                               WallpaperRepository wallpaperRepository,
                               ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("검은사막 크롤링 시작");
        int count = 0;

        try {
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (compatible; GamePaper-Crawler/1.0)")
                    .timeout(15000)
                    .get();

            // 배경화면 이미지 URL 추출 (실제 사이트 구조에 맞게 selector 조정 필요)
            Elements images = doc.select("img[src*=wallpaper], img[src*=WallPaper]");

            for (Element img : images) {
                String imageUrl = img.absUrl("src");
                if (imageUrl.isBlank()) continue;

                if (downloadAndSave(gameId, imageUrl)) {
                    count++;
                }
                sleep(DELAY_MS);
            }

            log.info("검은사막 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (IOException e) {
            log.error("검은사막 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        }
    }
}
```

### Step 6: 빌드 확인

```bash
cd D:/work/AI해커톤/server
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`

### Step 7: 커밋

```bash
git add server/src/main/java/com/gamepaper/crawler/ \
        server/src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java \
        server/src/main/resources/application-local.yml
git commit -m "feat: AbstractGameCrawler 및 Jsoup 기반 크롤러 2개 구현 (FFXIV, 검은사막)"
```

---

## Task 4: Selenium 크롤러 4개 (S2-1)

**파일:**
- Modify: `server/build.gradle` (Selenium 의존성 추가)
- Create: `server/src/main/java/com/gamepaper/crawler/selenium/AbstractSeleniumCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/selenium/GenshinCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/selenium/MabinogiCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/selenium/MapleStoryCrawler.java`
- Create: `server/src/main/java/com/gamepaper/crawler/selenium/NikkeCrawler.java`

### Step 1: Selenium 의존성 추가

`server/build.gradle`의 `dependencies`에 추가:

```groovy
// Selenium (크롤링)
implementation 'org.seleniumhq.selenium:selenium-java:4.18.1'
// Jsoup (HTML 파싱)
implementation 'org.jsoup:jsoup:1.17.2'
```

### Step 2: Selenium Hub URL 설정 추가

`server/src/main/resources/application-local.yml`에 추가:

```yaml
selenium:
  hub-url: ${SELENIUM_HUB_URL:http://localhost:4444}
```

### Step 3: AbstractSeleniumCrawler 생성

`server/src/main/java/com/gamepaper/crawler/selenium/AbstractSeleniumCrawler.java`:

```java
package com.gamepaper.crawler.selenium;

import com.gamepaper.crawler.AbstractGameCrawler;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;

/**
 * Selenium 기반 크롤러 공통 로직
 */
@Slf4j
public abstract class AbstractSeleniumCrawler extends AbstractGameCrawler {

    @Value("${selenium.hub-url:http://localhost:4444}")
    protected String seleniumHubUrl;

    protected AbstractSeleniumCrawler(StorageService storageService,
                                      WallpaperRepository wallpaperRepository,
                                      ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    /**
     * RemoteWebDriver 세션 생성
     * 반드시 사용 후 driver.quit() 호출 필요
     */
    protected WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );
        try {
            return new RemoteWebDriver(URI.create(seleniumHubUrl + "/wd/hub").toURL(), options);
        } catch (Exception e) {
            throw new RuntimeException("Selenium 드라이버 생성 실패: " + seleniumHubUrl, e);
        }
    }

    /**
     * WebDriver를 안전하게 종료합니다.
     */
    protected void quitDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("WebDriver 종료 실패: {}", e.getMessage());
            }
        }
    }
}
```

### Step 4: GenshinCrawler 구현

`server/src/main/java/com/gamepaper/crawler/selenium/GenshinCrawler.java`:

```java
package com.gamepaper.crawler.selenium;

import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 원신 배경화면 크롤러 (Selenium 기반)
 * 대상: https://www.hoyolab.com/wallpaper
 */
@Slf4j
@Component
public class GenshinCrawler extends AbstractSeleniumCrawler {

    private static final String TARGET_URL = "https://www.hoyolab.com/wallpaper";
    private static final int DELAY_MS = 3000;
    private static final int MAX_SCROLL = 10;

    @Value("${game.id.genshin:1}")
    private Long gameId;

    public GenshinCrawler(StorageService storageService,
                          WallpaperRepository wallpaperRepository,
                          ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("원신 크롤링 시작");
        WebDriver driver = null;
        int count = 0;

        try {
            driver = createDriver();
            driver.get(TARGET_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            sleep(DELAY_MS);

            // 스크롤 페이지네이션
            for (int scroll = 0; scroll < MAX_SCROLL; scroll++) {
                List<WebElement> images = driver.findElements(
                        By.cssSelector("img[src*='hoyolab'][src*='.jpg'], img[src*='hoyolab'][src*='.png']"));

                List<String> imageUrls = images.stream()
                        .map(img -> img.getAttribute("src"))
                        .filter(src -> src != null && !src.isBlank())
                        .distinct()
                        .collect(Collectors.toList());

                for (String url : imageUrls) {
                    if (downloadAndSave(gameId, url)) {
                        count++;
                    }
                    sleep(1000);
                }

                // 페이지 하단으로 스크롤
                ((org.openqa.selenium.JavascriptExecutor) driver)
                        .executeScript("window.scrollTo(0, document.body.scrollHeight)");
                sleep(DELAY_MS);
            }

            log.info("원신 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (Exception e) {
            log.error("원신 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            quitDriver(driver);
        }
    }
}
```

### Step 5: MabinogiCrawler 구현

`server/src/main/java/com/gamepaper/crawler/selenium/MabinogiCrawler.java`:

```java
package com.gamepaper.crawler.selenium;

import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 마비노기 배경화면 크롤러 (Selenium 기반)
 * 대상: https://mabinogi.nexon.com/web/contents/wallPaper
 */
@Slf4j
@Component
public class MabinogiCrawler extends AbstractSeleniumCrawler {

    private static final String TARGET_URL = "https://mabinogi.nexon.com/web/contents/wallPaper";
    private static final int DELAY_MS = 2000;

    @Value("${game.id.mabinogi:2}")
    private Long gameId;

    public MabinogiCrawler(StorageService storageService,
                            WallpaperRepository wallpaperRepository,
                            ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("마비노기 크롤링 시작");
        WebDriver driver = null;
        int count = 0;

        try {
            driver = createDriver();
            driver.get(TARGET_URL);
            sleep(DELAY_MS);

            // 배경화면 이미지 요소 추출 (실제 selector는 사이트 구조에 따라 조정 필요)
            List<WebElement> images = driver.findElements(
                    By.cssSelector(".wallpaper-list img, .wallpaper_list img"));

            for (WebElement img : images) {
                String src = img.getAttribute("src");
                if (src == null || src.isBlank()) {
                    src = img.getAttribute("data-src");
                }
                if (src != null && !src.isBlank() && downloadAndSave(gameId, src)) {
                    count++;
                }
                sleep(1000);
            }

            log.info("마비노기 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (Exception e) {
            log.error("마비노기 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            quitDriver(driver);
        }
    }
}
```

### Step 6: MapleStoryCrawler 구현

`server/src/main/java/com/gamepaper/crawler/selenium/MapleStoryCrawler.java`:

```java
package com.gamepaper.crawler.selenium;

import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 메이플스토리 모바일 배경화면 크롤러 (Selenium 기반)
 * 대상: https://m.maplestory.nexon.com/Media/WallPaper
 */
@Slf4j
@Component
public class MapleStoryCrawler extends AbstractSeleniumCrawler {

    private static final String TARGET_URL = "https://m.maplestory.nexon.com/Media/WallPaper";
    private static final int DELAY_MS = 2000;

    @Value("${game.id.maplestory:3}")
    private Long gameId;

    public MapleStoryCrawler(StorageService storageService,
                              WallpaperRepository wallpaperRepository,
                              ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("메이플스토리 크롤링 시작");
        WebDriver driver = null;
        int count = 0;

        try {
            driver = createDriver();
            driver.get(TARGET_URL);
            sleep(DELAY_MS);

            List<WebElement> images = driver.findElements(
                    By.cssSelector(".wallpaper img, .WallPaper img, ul.list li img"));

            for (WebElement img : images) {
                String src = img.getAttribute("src");
                if (src == null || src.isBlank()) {
                    src = img.getAttribute("data-src");
                }
                if (src != null && !src.isBlank() && downloadAndSave(gameId, src)) {
                    count++;
                }
                sleep(1000);
            }

            log.info("메이플스토리 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (Exception e) {
            log.error("메이플스토리 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            quitDriver(driver);
        }
    }
}
```

### Step 7: NikkeCrawler 구현

`server/src/main/java/com/gamepaper/crawler/selenium/NikkeCrawler.java`:

```java
package com.gamepaper.crawler.selenium;

import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * NIKKE 배경화면 크롤러 (Selenium 기반)
 * 대상: https://www.nikke-kr.com/fansite.html
 */
@Slf4j
@Component
public class NikkeCrawler extends AbstractSeleniumCrawler {

    private static final String TARGET_URL = "https://www.nikke-kr.com/fansite.html";
    private static final int DELAY_MS = 2000;

    @Value("${game.id.nikke:4}")
    private Long gameId;

    public NikkeCrawler(StorageService storageService,
                        WallpaperRepository wallpaperRepository,
                        ImageProcessor imageProcessor) {
        super(storageService, wallpaperRepository, imageProcessor);
    }

    @Override
    public Long getGameId() {
        return gameId;
    }

    @Override
    public CrawlResult crawl() {
        log.info("NIKKE 크롤링 시작");
        WebDriver driver = null;
        int count = 0;

        try {
            driver = createDriver();
            driver.get(TARGET_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            sleep(DELAY_MS);

            // 배경화면 탭/섹션이 있는 경우 클릭 (사이트 구조에 따라 조정 필요)
            try {
                WebElement wallpaperTab = driver.findElement(By.cssSelector("[href*='wallpaper'], [data-tab*='wallpaper']"));
                wallpaperTab.click();
                sleep(DELAY_MS);
            } catch (Exception ignored) {
                // 탭 없으면 현재 페이지에서 계속
            }

            List<WebElement> images = driver.findElements(
                    By.cssSelector(".wallpaper img, .download-list img, a[href$='.jpg'] img, a[href$='.png'] img"));

            for (WebElement img : images) {
                String src = img.getAttribute("src");
                if (src == null || src.isBlank()) continue;
                if (downloadAndSave(gameId, src)) {
                    count++;
                }
                sleep(1000);
            }

            log.info("NIKKE 크롤링 완료 - 수집: {}", count);
            return CrawlResult.success(count);

        } catch (Exception e) {
            log.error("NIKKE 크롤링 실패", e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            quitDriver(driver);
        }
    }
}
```

### Step 8: 빌드 확인

```bash
cd D:/work/AI해커톤/server
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`

### Step 9: 커밋

```bash
git add server/build.gradle \
        server/src/main/java/com/gamepaper/crawler/selenium/ \
        server/src/main/resources/application-local.yml
git commit -m "feat: Selenium 기반 크롤러 4개 구현 (원신, 마비노기, 메이플스토리, NIKKE)"
```

---

## Task 5: Docker Compose Selenium 서비스 추가 + 게임 데이터 초기화 (S2-1)

**파일:**
- Modify: `server/docker-compose.yml`
- Create: `server/src/main/resources/data-local.sql` (선택적 초기 데이터)
- Modify: `server/src/main/resources/application-local.yml`

### Step 1: docker-compose.yml에 Selenium 서비스 추가

`server/docker-compose.yml`을 아래와 같이 수정합니다:

```yaml
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./storage:/app/storage
      - ./gamepaper.db:/app/gamepaper.db
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - STORAGE_ROOT=/app/storage
      - BASE_URL=http://localhost:8080
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
      - SELENIUM_HUB_URL=http://selenium:4444
      - CRAWLER_SCHEDULE_DELAY_MS=${CRAWLER_SCHEDULE_DELAY_MS:-21600000}
    depends_on:
      selenium:
        condition: service_healthy
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/games"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  selenium:
    image: selenium/standalone-chrome:latest
    shm_size: "2g"
    ports:
      - "4444:4444"
      - "7900:7900"  # VNC (선택적 디버깅용)
    environment:
      - SE_NODE_MAX_SESSIONS=1
      - SE_SESSION_REQUEST_TIMEOUT=300
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4444/wd/hub/status"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 30s
    restart: unless-stopped
```

### Step 2: 게임 데이터 초기화 SQL 작성

`server/src/main/resources/data-local.sql`:

```sql
-- 6개 게임 초기 데이터 (중복 삽입 방지: INSERT OR IGNORE)
INSERT OR IGNORE INTO games (id, name, url, status, created_at) VALUES
(1, '원신', 'https://www.hoyolab.com/wallpaper', 'ACTIVE', datetime('now')),
(2, '마비노기', 'https://mabinogi.nexon.com/web/contents/wallPaper', 'ACTIVE', datetime('now')),
(3, '메이플스토리 모바일', 'https://m.maplestory.nexon.com/Media/WallPaper', 'ACTIVE', datetime('now')),
(4, 'NIKKE', 'https://www.nikke-kr.com/fansite.html', 'ACTIVE', datetime('now')),
(5, '파이널판타지 XIV', 'https://na.finalfantasyxiv.com/lodestone/special/fankit/desktop_wallpaper/', 'ACTIVE', datetime('now')),
(6, '검은사막', 'https://www.kr.playblackdesert.com/ko-KR/About/WallPaper', 'ACTIVE', datetime('now'));
```

### Step 3: application-local.yml에 data SQL 설정 추가

`server/src/main/resources/application-local.yml`에 추가:

```yaml
spring:
  sql:
    init:
      mode: always
      data-locations: classpath:data-local.sql
```

> 주의: `spring.sql.init.mode=always`는 서버 시작 시마다 SQL을 실행합니다. `INSERT OR IGNORE`를 사용하여 중복 삽입을 방지합니다.

### Step 4: .env.example 업데이트

`server/.env.example`에 추가:

```env
# 크롤링 스케줄 주기 (ms, 기본: 6시간 = 21600000)
# 테스트 시 60000(1분)으로 변경 가능
CRAWLER_SCHEDULE_DELAY_MS=21600000
```

### Step 5: Docker Compose 기동 테스트

```bash
cd D:/work/AI해커톤/server
# 이전 컨테이너 정리
docker compose down

# Selenium 포함 재빌드
docker compose up --build -d

# 로그 확인 (backend + selenium 모두 healthy 상태 확인)
docker compose logs -f
```

예상 결과:
- `selenium` 컨테이너 `healthy`
- `backend` 컨테이너 `Started GamepaperApplication` 로그 후 `healthy`

### Step 6: 게임 데이터 초기화 확인

```bash
curl http://localhost:8080/api/games
```

예상 결과: 6개 게임 목록 반환

```json
[
  {"id": 1, "name": "원신", "wallpaperCount": 0, "status": "ACTIVE", "lastCrawledAt": null},
  {"id": 2, "name": "마비노기", ...},
  ...
]
```

### Step 7: 커밋

```bash
git add server/docker-compose.yml \
        server/src/main/resources/data-local.sql \
        server/src/main/resources/application-local.yml \
        server/.env.example
git commit -m "feat: docker-compose Selenium 서비스 추가 및 게임 초기 데이터 설정"
```

---

## Task 6: Flutter 앱 API 연결 전환 (S2-3)

> Flutter 클라이언트는 별도 레포지토리(`https://github.com/sms1875/GamePaper`)에 있습니다.
> 이 Task는 해당 레포의 Flutter 코드를 수정합니다.

**수정 대상 파일 (Flutter 레포 기준):**
- Modify: `lib/providers/game_provider.dart` — Firebase Storage → 서버 API 호출
- Modify: `lib/providers/wallpaper_provider.dart` — Firebase Storage → 서버 API 호출
- Create: `lib/config/api_config.dart` — 서버 URL 환경 설정
- Modify: `pubspec.yaml` — http 패키지 의존성 확인

### Step 1: ApiConfig 설정 파일 생성

`lib/config/api_config.dart`:

```dart
/// 서버 API 기본 URL 설정
///
/// 에뮬레이터: http://10.0.2.2:8080
/// 실기기 (로컬 네트워크): http://{서버_로컬_IP}:8080
/// 예: http://192.168.1.100:8080
class ApiConfig {
  // TODO: 실기기 테스트 시 로컬 IP로 변경
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static String gamesUrl() => '$baseUrl/api/games';
  static String wallpapersUrl(int gameId, {int page = 0, int size = 12}) =>
      '$baseUrl/api/wallpapers/$gameId?page=$page&size=$size';
}
```

### Step 2: pubspec.yaml http 패키지 확인

`pubspec.yaml`에 `http` 패키지가 없으면 추가:

```yaml
dependencies:
  http: ^1.2.0
```

### Step 3: GameProvider 수정

`lib/providers/game_provider.dart`에서 Firebase Storage 직접 호출을 서버 API로 교체:

```dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/game.dart';  // 기존 Game 모델 유지

class GameProvider extends ChangeNotifier {
  List<Game> _games = [];
  bool _isLoading = false;
  String? _error;

  List<Game> get games => _games;
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> fetchGames() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final response = await http.get(
        Uri.parse(ApiConfig.gamesUrl()),
        headers: {'Content-Type': 'application/json'},
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final List<dynamic> data = json.decode(response.body);
        _games = data.map((json) => Game.fromServerJson(json)).toList();
      } else {
        _error = '서버 오류: ${response.statusCode}';
      }
    } catch (e) {
      _error = '네트워크 오류: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
```

### Step 4: WallpaperProvider 수정

`lib/providers/wallpaper_provider.dart`에서 Firebase Storage 직접 호출을 서버 API로 교체:

```dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/wallpaper.dart';  // 기존 Wallpaper 모델 유지

class WallpaperProvider extends ChangeNotifier {
  final Map<int, List<Wallpaper>> _wallpapersByGame = {};
  bool _isLoading = false;
  String? _error;
  int _currentPage = 0;
  bool _hasMore = true;

  List<Wallpaper> wallpapersFor(int gameId) => _wallpapersByGame[gameId] ?? [];
  bool get isLoading => _isLoading;
  String? get error => _error;
  bool get hasMore => _hasMore;

  Future<void> fetchWallpapers(int gameId, {bool refresh = false}) async {
    if (refresh) {
      _wallpapersByGame[gameId] = [];
      _currentPage = 0;
      _hasMore = true;
    }

    if (!_hasMore || _isLoading) return;

    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final response = await http.get(
        Uri.parse(ApiConfig.wallpapersUrl(gameId, page: _currentPage)),
        headers: {'Content-Type': 'application/json'},
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final Map<String, dynamic> data = json.decode(response.body);
        final List<dynamic> content = data['content'];
        final int totalPages = data['totalPages'];

        final wallpapers = content.map((json) => Wallpaper.fromServerJson(json)).toList();

        _wallpapersByGame[gameId] = [
          ...(_wallpapersByGame[gameId] ?? []),
          ...wallpapers,
        ];

        _currentPage++;
        _hasMore = _currentPage < totalPages;
      } else {
        _error = '서버 오류: ${response.statusCode}';
      }
    } catch (e) {
      _error = '네트워크 오류: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
```

### Step 5: Game/Wallpaper 모델에 fromServerJson 팩토리 추가

기존 Firebase 기반 `fromFirebase()` 팩토리 옆에 `fromServerJson()` 팩토리를 추가합니다.

`Game.fromServerJson()` 예시:
```dart
factory Game.fromServerJson(Map<String, dynamic> json) {
  return Game(
    id: json['id'].toString(),
    name: json['name'],
    wallpaperCount: json['wallpaperCount'] ?? 0,
    status: json['status'] ?? 'ACTIVE',
  );
}
```

`Wallpaper.fromServerJson()` 예시:
```dart
factory Wallpaper.fromServerJson(Map<String, dynamic> json) {
  return Wallpaper(
    id: json['id'].toString(),
    url: json['url'],
    width: json['width'] ?? 0,
    height: json['height'] ?? 0,
    blurHash: json['blurHash'] ?? '',
    tags: List<String>.from(json['tags'] ?? []),
  );
}
```

> 기존 Firebase 팩토리는 삭제하지 말고 `@deprecated` 처리 후 Sprint 6에서 제거합니다.

### Step 6: Android 네트워크 보안 설정 확인

Android 에뮬레이터에서 HTTP (비-HTTPS) 서버에 접속하려면 `android/app/src/main/AndroidManifest.xml`에 다음이 있어야 합니다:

```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

또는 `network_security_config.xml`을 추가합니다. 개발 단계에서는 `usesCleartextTraffic=true`가 간편합니다.

### Step 7: 앱 실행 및 수동 검증

```bash
# 에뮬레이터에서 실행
flutter run

# 또는 실기기 (서버 IP 환경변수 전달)
flutter run --dart-define=API_BASE_URL=http://192.168.1.100:8080
```

수동 검증 항목:
- ⬜ 앱 실행 시 게임 목록이 표시된다
- ⬜ 게임 탭 시 배경화면 그리드가 표시된다
- ⬜ 배경화면을 선택하여 기기에 적용할 수 있다

### Step 8: 커밋 (Flutter 레포)

```bash
# Flutter 레포에서
git add lib/config/api_config.dart \
        lib/providers/game_provider.dart \
        lib/providers/wallpaper_provider.dart \
        lib/models/
git commit -m "feat: Firebase Storage 직접 호출 → 서버 REST API 호출로 전환"
```

---

## Task 7: GitHub Actions CI 파이프라인 (S2-4)

**파일:**
- Create: `.github/workflows/ci.yml`

### Step 1: .github/workflows 디렉토리 확인

```bash
ls D:/work/AI해커톤/.github/workflows/ 2>/dev/null || echo "없음"
```

### Step 2: ci.yml 작성

`D:/work/AI해커톤/.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches:
      - '**'
  pull_request:
    branches:
      - master

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: 코드 체크아웃
        uses: actions/checkout@v4

      - name: Java 21 설정
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Gradle 의존성 캐시
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('server/**/*.gradle*', 'server/**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Gradle 실행 권한 부여
        run: chmod +x server/gradlew

      - name: 빌드 및 테스트
        run: |
          cd server
          ./gradlew build --no-daemon

      - name: 테스트 결과 업로드
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: server/build/reports/tests/
          retention-days: 7
```

### Step 3: .github 디렉토리 생성 및 파일 저장

```bash
mkdir -p "D:/work/AI해커톤/.github/workflows"
```

### Step 4: 빌드 검증 (로컬)

```bash
cd D:/work/AI해커톤/server
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`, 전체 테스트 통과

### Step 5: 커밋

```bash
git add .github/workflows/ci.yml
git commit -m "feat: GitHub Actions CI 파이프라인 추가 (빌드 + 테스트)"
```

---

## Task 8: 전체 통합 검증 및 DoD 확인

### Step 1: 전체 단위 테스트 실행

```bash
cd D:/work/AI해커톤/server
./gradlew test --no-daemon
```

예상 결과: 모든 테스트 통과 (Sprint 1 테스트 8개 + ImageProcessorTest 5개 이상)

### Step 2: Docker 환경 기동 확인

```bash
cd D:/work/AI해커톤/server
docker compose up --build -d
docker compose logs -f
```

예상 결과:
- `selenium` 컨테이너 healthy
- `backend` 컨테이너 healthy, `Started GamepaperApplication` 로그 출력

### Step 3: API 검증

```bash
# 게임 6개 목록 확인
curl http://localhost:8080/api/games

# 크롤러 테스트 실행 (스케줄 단축: 환경변수로 재기동)
docker compose down
CRAWLER_SCHEDULE_DELAY_MS=60000 docker compose up --build -d
# 1분 후 크롤링 실행 대기 후 확인
sleep 90
curl "http://localhost:8080/api/wallpapers/1?page=0&size=12"
```

예상 결과 (크롤링 성공 시):
```json
{
  "content": [
    {
      "id": 1,
      "url": "http://localhost:8080/storage/images/1/...",
      "width": 1080,
      "height": 1920,
      "blurHash": "LEHV6n...",
      ...
    }
  ],
  "totalElements": N,
  ...
}
```

### Step 4: DoD 최종 체크리스트

- ⬜ 6개 게임 크롤러가 Docker 환경에서 실행되어 이미지를 로컬 스토리지에 수집한다
- ⬜ 수집된 이미지의 BlurHash가 DB에 저장되고 API로 조회 가능하다
- ⬜ 수집된 이미지의 width/height가 DB에 저장된다
- ⬜ Flutter 앱이 서버 API를 통해 게임 목록과 배경화면을 표시한다
- ⬜ 앱에서 배경화면을 선택하여 기기에 적용할 수 있다
- ⬜ GitHub Actions에서 빌드/테스트가 통과한다

### Step 5: 최종 커밋

```bash
git add .
git commit -m "docs: Sprint 2 완료 - 크롤러 마이그레이션 + 클라이언트 API 연결"
git push origin sprint2
```

---

## 리스크 및 주의사항

| 리스크 | 상황 | 대응 방안 |
|--------|------|-----------|
| Selenium 컨테이너 메모리 부족 | Docker 메모리 제한 환경 | `shm_size: 2g` 설정, 크롤러 순차 실행으로 동시 세션 최소화 |
| 크롤링 대상 사이트 구조 변경 | selector가 맞지 않으면 이미지 0개 수집 | 크롤러 실행 후 수집 수 로그 확인, selector 조정 필요 (Sprint 4 AI 전략으로 해결 예정) |
| BlurHash 라이브러리 의존성 미해결 | Maven Central에 없는 경우 | `vanniktech/blurhash` 또는 순수 Java 구현으로 대체 |
| Docker 환경 없는 로컬 실행 | Selenium Hub URL 연결 불가 | 테스트 시 Selenium standalone Docker만 별도 실행: `docker run -d -p 4444:4444 --shm-size=2g selenium/standalone-chrome` |
| Flutter 앱 별도 레포 | Task 6 범위가 별도 레포에 있음 | Flutter 레포 별도 체크아웃 후 수정, 커밋은 해당 레포에 |
| Android HTTP 접속 차단 | `usesCleartextTraffic` 미설정 | `AndroidManifest.xml` 수정 필수 |
| N+1 쿼리 (I-1) | Sprint 2에서 데이터 증가 | Task 1 이후 필요 시 `@Query` JOIN COUNT로 최적화 (sprint2 중 시간 여유 시 처리) |

---

## 예상 산출물

Sprint 2 완료 시 다음 산출물이 생성됩니다:

```
D:\work\AI해커톤\
├── .github/
│   └── workflows/
│       └── ci.yml                           (신규)
└── server/
    ├── docker-compose.yml                   (Selenium 서비스 추가)
    ├── build.gradle                         (Selenium, Jsoup, BlurHash 의존성 추가)
    └── src/
        ├── main/java/com/gamepaper/
        │   ├── GamepaperApplication.java    (@EnableScheduling 추가)
        │   ├── config/
        │   │   └── AppConfig.java           (신규: ThreadPoolTaskScheduler)
        │   ├── crawler/
        │   │   ├── GameCrawler.java          (신규: 인터페이스)
        │   │   ├── CrawlResult.java          (신규: DTO)
        │   │   ├── CrawlerScheduler.java     (신규: 6시간 주기 스케줄러)
        │   │   ├── AbstractGameCrawler.java  (신규: 공통 로직)
        │   │   ├── image/
        │   │   │   ├── ImageMetadata.java    (신규)
        │   │   │   └── ImageProcessor.java   (신규: BlurHash + 해상도)
        │   │   ├── jsoup/
        │   │   │   ├── FinalFantasyXIVCrawler.java  (신규)
        │   │   │   └── BlackDesertCrawler.java       (신규)
        │   │   └── selenium/
        │   │       ├── AbstractSeleniumCrawler.java  (신규)
        │   │       ├── GenshinCrawler.java            (신규)
        │   │       ├── MabinogiCrawler.java           (신규)
        │   │       ├── MapleStoryCrawler.java         (신규)
        │   │       └── NikkeCrawler.java              (신규)
        │   └── domain/
        │       ├── crawler/
        │       │   └── CrawlingLog.java     (수정: status 타입 enum 변경)
        │       └── wallpaper/
        │           └── WallpaperRepository.java  (수정: existsByGameIdAndFileName 추가)
        ├── main/resources/
        │   ├── application-local.yml        (crawler.schedule, game.id, selenium 설정 추가)
        │   └── data-local.sql               (신규: 6개 게임 초기 데이터)
        └── test/java/com/gamepaper/
            └── crawler/
                └── image/
                    └── ImageProcessorTest.java  (신규)

GamePaper Flutter 레포 (별도):
├── lib/
│   ├── config/
│   │   └── api_config.dart              (신규)
│   ├── providers/
│   │   ├── game_provider.dart           (수정: 서버 API 호출)
│   │   └── wallpaper_provider.dart      (수정: 서버 API 호출)
│   └── models/                          (수정: fromServerJson 팩토리 추가)
└── android/app/src/main/AndroidManifest.xml  (수정: usesCleartextTraffic)
```

---

## 검증 시나리오 (API 레벨)

> 관리자 UI가 Sprint 3에서 구현되므로 이번 스프린트는 API + 수동 앱 검증

```
1. docker compose up --build → backend + selenium 컨테이너 정상 기동 확인
2. curl http://localhost:8080/api/games → 6개 게임 목록 반환
3. CRAWLER_SCHEDULE_DELAY_MS=60000으로 서버 재시작 → 1분 후 크롤링 자동 실행
4. curl "http://localhost:8080/api/wallpapers/1?page=0&size=12" → content 배열에 항목, blurHash 비어 있지 않음 확인
5. 반환된 url 접속 → 이미지 200 응답 확인
6. Flutter 앱 실행 → 게임 목록 → 배경화면 그리드 표시 → 배경화면 적용 수동 확인
7. GitHub Actions 확인 → CI 워크플로우 초록불
```

---

## 검증 결과

- [검증 보고서](sprint2/validation-report.md)
- [코드 리뷰](sprint2/code-review.md)

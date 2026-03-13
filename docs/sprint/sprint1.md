# Sprint 1: 백엔드 인프라 추상화 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Spring Boot 프로젝트를 초기화하고, StorageService/Repository 추상화 레이어와 Docker 로컬 환경을 구축하여 `GET /api/games` 가 정상 응답하는 백엔드 기반을 완성한다.

**Architecture:** Spring Boot 3.x (Java 21) + SQLite(JPA) + 로컬 파일 스토리지를 Docker Compose로 묶어 실행한다. StorageService와 Repository는 인터페이스로 추상화하여 Phase 2+에서 클라우드 구현체로 교체 가능하도록 설계한다. Spring Profile(`local`)로 구현체를 자동 선택한다.

**Tech Stack:** Java 21, Spring Boot 3.x, Gradle, Spring Data JPA, SQLite JDBC, Thymeleaf, Docker Compose

**브랜치:** `sprint1` (master에서 분기, 이미 생성됨)

---

## 스프린트 목표 및 범위

### 포함 항목

- S1-1: Spring Boot 프로젝트 초기화 (패키지 구조, 의존성, 프로파일 설정)
- S1-2: StorageService 추상화 + LocalStorageService 구현 (`listFiles()`는 제외 — Sprint 3으로 이동)
- S1-3: DB Repository 추상화 구현 (Game, Wallpaper, CrawlingLog 엔티티 + JPA)
- S1-4: 클라이언트용 REST API (`GET /api/games`, `GET /api/wallpapers/{gameId}`)
- S1-5: Docker Compose 로컬 환경 구성 (멀티스테이지 Dockerfile + 볼륨 마운트)

### 제외 항목 (Sprint 1 범위 밖)

- `StorageService.listFiles()` — Sprint 3 Admin UI 배경화면 탭 구현 시 추가
- Selenium 크롤러 마이그레이션 — Sprint 2
- Admin UI (Thymeleaf 페이지) — Sprint 3
- GitHub Actions CI — Sprint 2
- BlurHash 처리 — Sprint 2

---

## 완료 기준 (Definition of Done)

| 기준 | 검증 명령어 |
|------|-------------|
| docker compose up으로 서버 정상 기동 | `docker compose up --build` → 로그에 `Started GamepaperApplication` 확인 |
| GET /api/games 빈 목록 반환 | `curl http://localhost:8080/api/games` → `[]` |
| SQLite DB 파일 생성 및 데이터 영속성 | 서버 재시작 후 `curl http://localhost:8080/api/games` → 이전 데이터 유지 |
| 로컬 스토리지 이미지 영구 URL 접근 | 이미지 파일 복사 후 URL 접근 → 200 응답 |
| SQLite WAL 모드 활성화 | `sqlite3 gamepaper.db "PRAGMA journal_mode;"` → `wal` |

---

## 의존성 순서 (구현 실행 순서)

```
S1-1 (프로젝트 초기화)
  └→ S1-3 (DB 엔티티/Repository) — 프로젝트 구조 필요
       └→ S1-2 (StorageService) — 병렬 가능하지만 Config 클래스 공유
            └→ S1-4 (REST API) — 엔티티 + StorageService 모두 필요
                 └→ S1-5 (Docker Compose) — JAR 빌드 결과 필요
```

---

## 기술 결정 사항

| 결정 항목 | 결정 내용 | 이유 |
|-----------|-----------|------|
| DB 스키마 생성 방식 | `spring.jpa.hibernate.ddl-auto=update` (초기) | 개발 단계 단순화, Sprint 6+ Flyway 전환 예정 |
| SQLite WAL 모드 활성화 | `JdbcTemplate`으로 서버 시작 시 `PRAGMA journal_mode=WAL` 실행 | SQLite 동시 쓰기 제한 완화 |
| 정적 파일 서빙 경로 | Spring `ResourceHandlerRegistry`로 `/storage/**` → `{STORAGE_ROOT}/` 매핑 | 별도 웹서버 불필요, 영구 URL |
| `listFiles()` 구현 여부 | 인터페이스 선언만, 구현은 Sprint 3으로 이동 | YAGNI — Sprint 1에서 사용처 없음 |
| 페이지네이션 기본값 | page=0, size=12 | PRD 스펙 준수 |
| `@Profile("local")` 적용 | `LocalStorageService`에만 적용 | 향후 `@Profile("prod")` 구현체 추가 대비 |
| Docker 볼륨 마운트 | 호스트 `./storage` 및 `./gamepaper.db` 직접 바인드 | 컨테이너 재시작 시 데이터 유지 |

---

## Task 1: Spring Boot 프로젝트 초기화 (S1-1)

**파일:**
- Create: `build.gradle`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-local.yml`
- Create: `src/main/java/com/gamepaper/GamepaperApplication.java`
- Create: `src/main/java/com/gamepaper/config/WebConfig.java`

### Step 1: Gradle 프로젝트 생성 및 의존성 추가

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.3'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.gamepaper'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'
    implementation 'org.hibernate.orm:hibernate-community-dialects:6.4.4.Final'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### Step 2: 메인 Application 클래스 생성

`src/main/java/com/gamepaper/GamepaperApplication.java`:

```java
package com.gamepaper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GamepaperApplication {
    public static void main(String[] args) {
        SpringApplication.run(GamepaperApplication.class, args);
    }
}
```

### Step 3: 공통 application.yml 작성

`src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: local
  application:
    name: gamepaper

server:
  port: 8080
```

### Step 4: local 프로파일 설정 작성

`src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:${STORAGE_ROOT:/app}/gamepaper.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true

storage:
  root: ${STORAGE_ROOT:/app/storage}
  base-url: ${BASE_URL:http://localhost:8080}
```

### Step 5: CORS 설정 (Flutter 앱 접근 허용)

`src/main/java/com/gamepaper/config/WebConfig.java`:

```java
package com.gamepaper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
```

### Step 6: 빌드 확인

```bash
cd D:/work/AI해커톤
./gradlew build --no-daemon
```

예상 결과: `BUILD SUCCESSFUL`

### Step 7: 커밋

```bash
git add build.gradle src/main/resources/ src/main/java/com/gamepaper/GamepaperApplication.java src/main/java/com/gamepaper/config/
git commit -m "feat: Spring Boot 프로젝트 초기화 및 프로파일 설정"
```

---

## Task 2: DB 엔티티 및 Repository 구현 (S1-3)

**파일:**
- Create: `src/main/java/com/gamepaper/domain/game/GameStatus.java`
- Create: `src/main/java/com/gamepaper/domain/game/Game.java`
- Create: `src/main/java/com/gamepaper/domain/game/GameRepository.java`
- Create: `src/main/java/com/gamepaper/domain/wallpaper/Wallpaper.java`
- Create: `src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`
- Create: `src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java`
- Create: `src/main/java/com/gamepaper/domain/crawler/CrawlingLogRepository.java`
- Create: `src/main/java/com/gamepaper/config/DatabaseConfig.java`
- Test: `src/test/java/com/gamepaper/domain/game/GameRepositoryTest.java`

### Step 1: GameStatus enum 생성

`src/main/java/com/gamepaper/domain/game/GameStatus.java`:

```java
package com.gamepaper.domain.game;

public enum GameStatus {
    ACTIVE,
    UPDATING,
    FAILED
}
```

### Step 2: Game 엔티티 생성

`src/main/java/com/gamepaper/domain/game/Game.java`:

```java
package com.gamepaper.domain.game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GameStatus status = GameStatus.ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_crawled_at")
    private LocalDateTime lastCrawledAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Game(String name, String url) {
        this.name = name;
        this.url = url;
        this.status = GameStatus.ACTIVE;
    }
}
```

### Step 3: GameRepository 생성

`src/main/java/com/gamepaper/domain/game/GameRepository.java`:

```java
package com.gamepaper.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findAllByStatus(GameStatus status);
}
```

### Step 4: Wallpaper 엔티티 생성

`src/main/java/com/gamepaper/domain/wallpaper/Wallpaper.java`:

```java
package com.gamepaper.domain.wallpaper;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallpapers")
@Getter
@Setter
@NoArgsConstructor
public class Wallpaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(length = 500)
    private String url;

    private Integer width;

    private Integer height;

    @Column(name = "blur_hash", length = 100)
    private String blurHash;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_type", length = 20)
    private String imageType = "mobile";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Wallpaper(Long gameId, String fileName, String url) {
        this.gameId = gameId;
        this.fileName = fileName;
        this.url = url;
    }
}
```

### Step 5: WallpaperRepository 생성

`src/main/java/com/gamepaper/domain/wallpaper/WallpaperRepository.java`:

```java
package com.gamepaper.domain.wallpaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WallpaperRepository extends JpaRepository<Wallpaper, Long> {
    Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
    long countByGameId(Long gameId);
}
```

### Step 6: CrawlingLog 엔티티 생성

`src/main/java/com/gamepaper/domain/crawler/CrawlingLog.java`:

```java
package com.gamepaper.domain.crawler;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawling_logs")
@Getter
@Setter
@NoArgsConstructor
public class CrawlingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "collected_count")
    private Integer collectedCount = 0;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
```

### Step 7: CrawlingLogRepository 생성

`src/main/java/com/gamepaper/domain/crawler/CrawlingLogRepository.java`:

```java
package com.gamepaper.domain.crawler;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrawlingLogRepository extends JpaRepository<CrawlingLog, Long> {
    List<CrawlingLog> findTop10ByOrderByStartedAtDesc();
    List<CrawlingLog> findAllByGameIdOrderByStartedAtDesc(Long gameId);
}
```

### Step 8: SQLite WAL 모드 활성화 설정

`src/main/java/com/gamepaper/config/DatabaseConfig.java`:

```java
package com.gamepaper.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void enableWalMode() {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
    }
}
```

### Step 9: GameRepository 통합 테스트 작성

`src/test/java/com/gamepaper/domain/game/GameRepositoryTest.java`:

```java
package com.gamepaper.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("게임 저장 및 조회")
    void saveAndFind() {
        Game game = new Game("원신", "https://hoyolab.com");
        Game saved = gameRepository.save(game);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("원신");
        assertThat(saved.getStatus()).isEqualTo(GameStatus.ACTIVE);
    }

    @Test
    @DisplayName("상태별 게임 목록 조회")
    void findByStatus() {
        gameRepository.save(new Game("원신", "https://hoyolab.com"));
        Game failedGame = new Game("NIKKE", "https://nikke-kr.com");
        failedGame.setStatus(GameStatus.FAILED);
        gameRepository.save(failedGame);

        List<Game> activeGames = gameRepository.findAllByStatus(GameStatus.ACTIVE);
        assertThat(activeGames).hasSize(1);
        assertThat(activeGames.get(0).getName()).isEqualTo("원신");
    }
}
```

### Step 10: 테스트 실행

```bash
./gradlew test --tests "com.gamepaper.domain.game.GameRepositoryTest" --no-daemon
```

예상 결과: `2 tests completed, 0 failed`

### Step 11: 커밋

```bash
git add src/main/java/com/gamepaper/domain/ src/main/java/com/gamepaper/config/DatabaseConfig.java src/test/java/com/gamepaper/domain/
git commit -m "feat: Game/Wallpaper/CrawlingLog 엔티티 및 JPA Repository 구현"
```

---

## Task 3: StorageService 추상화 구현 (S1-2)

**파일:**
- Create: `src/main/java/com/gamepaper/storage/StorageService.java`
- Create: `src/main/java/com/gamepaper/storage/local/LocalStorageService.java`
- Create: `src/main/java/com/gamepaper/config/StorageConfig.java`
- Test: `src/test/java/com/gamepaper/storage/LocalStorageServiceTest.java`

### Step 1: StorageService 인터페이스 정의

`src/main/java/com/gamepaper/storage/StorageService.java`:

```java
package com.gamepaper.storage;

import java.util.List;

public interface StorageService {

    /**
     * 이미지 파일 업로드
     * @return 서빙 가능한 영구 URL
     */
    String upload(Long gameId, String fileName, byte[] data);

    /**
     * 파일의 영구 서빙 URL 반환
     */
    String getUrl(Long gameId, String fileName);

    /**
     * 파일 삭제
     */
    void delete(Long gameId, String fileName);

    /**
     * 게임 폴더 내 파일 목록 (Sprint 3에서 구현)
     */
    List<String> listFiles(Long gameId);
}
```

### Step 2: LocalStorageService 구현

`src/main/java/com/gamepaper/storage/local/LocalStorageService.java`:

```java
package com.gamepaper.storage.local;

import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    @Value("${storage.root:/app/storage}")
    private String storageRoot;

    @Value("${storage.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public String upload(Long gameId, String fileName, byte[] data) {
        Path dir = Paths.get(storageRoot, "images", String.valueOf(gameId));
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, data);
            log.debug("파일 저장 완료: {}", filePath);
            return getUrl(gameId, fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + fileName, e);
        }
    }

    @Override
    public String getUrl(Long gameId, String fileName) {
        return baseUrl + "/storage/images/" + gameId + "/" + fileName;
    }

    @Override
    public void delete(Long gameId, String fileName) {
        Path filePath = Paths.get(storageRoot, "images", String.valueOf(gameId), fileName);
        try {
            Files.deleteIfExists(filePath);
            log.debug("파일 삭제 완료: {}", filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제 실패: " + fileName, e);
        }
    }

    @Override
    public List<String> listFiles(Long gameId) {
        // Sprint 3 Admin UI 구현 시 추가 예정
        throw new UnsupportedOperationException("listFiles()는 Sprint 3에서 구현 예정");
    }
}
```

### Step 3: 정적 파일 서빙 경로 등록

`src/main/java/com/gamepaper/config/StorageConfig.java`:

```java
package com.gamepaper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorageConfig implements WebMvcConfigurer {

    @Value("${storage.root:/app/storage}")
    private String storageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /storage/** 요청을 실제 파일 시스템 경로로 매핑
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("file:" + storageRoot + "/");
    }
}
```

### Step 4: LocalStorageService 단위 테스트 작성

`src/test/java/com/gamepaper/storage/LocalStorageServiceTest.java`:

```java
package com.gamepaper.storage;

import com.gamepaper.storage.local.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    LocalStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new LocalStorageService();
        setField(storageService, "storageRoot", tempDir.toString());
        setField(storageService, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("파일 업로드 후 URL 반환")
    void uploadReturnsUrl() {
        byte[] data = "test-image".getBytes();
        String url = storageService.upload(1L, "test.jpg", data);

        assertThat(url).isEqualTo("http://localhost:8080/storage/images/1/test.jpg");
    }

    @Test
    @DisplayName("업로드한 파일이 실제로 저장됨")
    void uploadSavesFile() throws IOException {
        byte[] data = "test-image".getBytes();
        storageService.upload(1L, "test.jpg", data);

        Path savedFile = tempDir.resolve("images/1/test.jpg");
        assertThat(Files.exists(savedFile)).isTrue();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(data);
    }

    @Test
    @DisplayName("파일 삭제")
    void deleteRemovesFile() throws IOException {
        byte[] data = "test-image".getBytes();
        storageService.upload(1L, "test.jpg", data);
        storageService.delete(1L, "test.jpg");

        Path savedFile = tempDir.resolve("images/1/test.jpg");
        assertThat(Files.exists(savedFile)).isFalse();
    }

    @Test
    @DisplayName("getUrl은 파일 존재 여부와 무관하게 URL 반환")
    void getUrlReturnsCorrectUrl() {
        String url = storageService.getUrl(2L, "image.png");
        assertThat(url).isEqualTo("http://localhost:8080/storage/images/2/image.png");
    }

    // 테스트용 리플렉션 헬퍼
    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
```

### Step 5: 테스트 실행

```bash
./gradlew test --tests "com.gamepaper.storage.LocalStorageServiceTest" --no-daemon
```

예상 결과: `4 tests completed, 0 failed`

### Step 6: 커밋

```bash
git add src/main/java/com/gamepaper/storage/ src/main/java/com/gamepaper/config/StorageConfig.java src/test/java/com/gamepaper/storage/
git commit -m "feat: StorageService 추상화 및 LocalStorageService 구현"
```

---

## Task 4: 클라이언트 REST API 구현 (S1-4)

**파일:**
- Create: `src/main/java/com/gamepaper/api/dto/GameDto.java`
- Create: `src/main/java/com/gamepaper/api/dto/WallpaperDto.java`
- Create: `src/main/java/com/gamepaper/api/dto/PagedResponse.java`
- Create: `src/main/java/com/gamepaper/api/GameApiController.java`
- Create: `src/main/java/com/gamepaper/api/WallpaperApiController.java`
- Test: `src/test/java/com/gamepaper/api/GameApiControllerTest.java`

### Step 1: GameDto 생성

`src/main/java/com/gamepaper/api/dto/GameDto.java`:

```java
package com.gamepaper.api.dto;

import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GameDto {

    private final Long id;
    private final String name;
    private final long wallpaperCount;
    private final GameStatus status;
    private final LocalDateTime lastCrawledAt;

    public GameDto(Game game, long wallpaperCount) {
        this.id = game.getId();
        this.name = game.getName();
        this.wallpaperCount = wallpaperCount;
        this.status = game.getStatus();
        this.lastCrawledAt = game.getLastCrawledAt();
    }
}
```

### Step 2: WallpaperDto 생성

`src/main/java/com/gamepaper/api/dto/WallpaperDto.java`:

```java
package com.gamepaper.api.dto;

import com.gamepaper.domain.wallpaper.Wallpaper;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WallpaperDto {

    private final Long id;
    private final String url;
    private final Integer width;
    private final Integer height;
    private final String blurHash;
    private final String tags;
    private final String description;
    private final String imageType;
    private final LocalDateTime createdAt;

    public WallpaperDto(Wallpaper wallpaper) {
        this.id = wallpaper.getId();
        this.url = wallpaper.getUrl();
        this.width = wallpaper.getWidth();
        this.height = wallpaper.getHeight();
        this.blurHash = wallpaper.getBlurHash();
        this.tags = wallpaper.getTags();
        this.description = wallpaper.getDescription();
        this.imageType = wallpaper.getImageType();
        this.createdAt = wallpaper.getCreatedAt();
    }
}
```

### Step 3: PagedResponse 생성

`src/main/java/com/gamepaper/api/dto/PagedResponse.java`:

```java
package com.gamepaper.api.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int currentPage;
    private final int size;

    public PagedResponse(Page<T> page) {
        this.content = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.currentPage = page.getNumber();
        this.size = page.getSize();
    }
}
```

### Step 4: GameApiController 구현

`src/main/java/com/gamepaper/api/GameApiController.java`:

```java
package com.gamepaper.api;

import com.gamepaper.api.dto.GameDto;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameApiController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;

    @GetMapping
    public List<GameDto> getGames() {
        List<Game> games = gameRepository.findAll();
        return games.stream()
                .map(game -> new GameDto(game, wallpaperRepository.countByGameId(game.getId())))
                .collect(Collectors.toList());
    }
}
```

### Step 5: WallpaperApiController 구현

`src/main/java/com/gamepaper/api/WallpaperApiController.java`:

```java
package com.gamepaper.api;

import com.gamepaper.api.dto.PagedResponse;
import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperApiController {

    private final WallpaperRepository wallpaperRepository;

    @GetMapping("/{gameId}")
    public PagedResponse<WallpaperDto> getWallpapers(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WallpaperDto> result = wallpaperRepository
                .findAllByGameId(gameId, pageable)
                .map(WallpaperDto::new);

        return new PagedResponse<>(result);
    }
}
```

### Step 6: GameApiController 통합 테스트 작성

`src/test/java/com/gamepaper/api/GameApiControllerTest.java`:

```java
package com.gamepaper.api;

import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=local"
})
class GameApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GameRepository gameRepository;

    @BeforeEach
    void setUp() {
        gameRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/games - 게임 없을 때 빈 목록 반환")
    void getGamesEmpty() throws Exception {
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/games - 게임 등록 후 목록 반환")
    void getGamesWithData() throws Exception {
        gameRepository.save(new Game("원신", "https://hoyolab.com"));

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("원신")))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$[0].wallpaperCount", is(0)));
    }
}
```

### Step 7: 테스트 실행

```bash
./gradlew test --tests "com.gamepaper.api.GameApiControllerTest" --no-daemon
```

예상 결과: `2 tests completed, 0 failed`

### Step 8: 커밋

```bash
git add src/main/java/com/gamepaper/api/ src/test/java/com/gamepaper/api/
git commit -m "feat: 클라이언트용 REST API 구현 (GET /api/games, GET /api/wallpapers/{gameId})"
```

---

## Task 5: Docker Compose 로컬 환경 구성 (S1-5)

**파일:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `.dockerignore`

### Step 1: Dockerfile 작성 (멀티스테이지 빌드)

`Dockerfile`:

```dockerfile
# Stage 1: 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# 스토리지 디렉토리 미리 생성
RUN mkdir -p /app/storage/images

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 2: docker-compose.yml 작성

`docker-compose.yml`:

```yaml
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./storage:/app/storage        # 이미지 파일 영속화
      - ./gamepaper.db:/app/gamepaper.db  # SQLite DB 영속화
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - STORAGE_ROOT=/app/storage
      - BASE_URL=http://localhost:8080
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/games"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

> 참고: TECH-SPEC.md에 Selenium 서비스가 포함되어 있으나, Sprint 1은 크롤러 구현 범위 밖이므로 제외. Sprint 2에서 추가.

### Step 3: .env.example 작성

`.env.example`:

```env
# Claude API 키 (Phase 2 AI 크롤러 구현 시 필요)
ANTHROPIC_API_KEY=sk-ant-your-key-here

# 서버 포트 (기본값: 8080)
SERVER_PORT=8080

# 이미지 스토리지 루트 경로 (컨테이너 내부 경로)
STORAGE_ROOT=/app/storage

# 서버 베이스 URL (이미지 서빙 URL 생성에 사용)
BASE_URL=http://localhost:8080
```

### Step 4: .dockerignore 작성

`.dockerignore`:

```
.git
.gradle
build/
*.db
storage/
.env
.env.*
!.env.example
docs/
*.md
```

### Step 5: gamepaper.db 초기 파일 생성 (Docker 볼륨 마운트 전처리)

Docker Compose의 볼륨 마운트(`./gamepaper.db:/app/gamepaper.db`)는 파일이 없으면 디렉토리로 마운트됩니다. 서버 첫 실행 전에 빈 파일을 미리 생성해야 합니다.

```bash
# 호스트에서 실행
touch gamepaper.db
mkdir -p storage/images
```

### Step 6: Docker Compose 빌드 및 기동 확인

```bash
# 환경변수 파일 복사
cp .env.example .env

# 빌드 + 기동
docker compose up --build -d

# 로그 확인 (Started GamepaperApplication 메시지 대기)
docker compose logs -f backend
```

예상 결과: 약 60초 후 `Started GamepaperApplication in X.XXX seconds` 로그 출력

### Step 7: DoD 검증

```bash
# 1. API 응답 확인
curl http://localhost:8080/api/games
# 예상: []

# 2. 테스트 데이터 삽입 후 재확인
sqlite3 gamepaper.db "INSERT INTO games (name, url, status, created_at) VALUES ('원신', 'https://hoyolab.com', 'ACTIVE', datetime('now'));"
curl http://localhost:8080/api/games
# 예상: [{"id":1,"name":"원신","wallpaperCount":0,"status":"ACTIVE","lastCrawledAt":null}]

# 3. 서버 재시작 후 데이터 유지 확인
docker compose restart backend
sleep 30
curl http://localhost:8080/api/games
# 예상: 동일 데이터 유지

# 4. WAL 모드 확인
sqlite3 gamepaper.db "PRAGMA journal_mode;"
# 예상: wal

# 5. 이미지 서빙 확인
mkdir -p storage/images/1
echo "fake-image" > storage/images/1/test.jpg
curl -I http://localhost:8080/storage/images/1/test.jpg
# 예상: HTTP/1.1 200
```

### Step 8: 커밋

```bash
git add Dockerfile docker-compose.yml .env.example .dockerignore
git commit -m "chore: Docker Compose 로컬 환경 구성 (멀티스테이지 빌드)"
```

---

## Task 6: 전체 테스트 실행 및 최종 확인

### Step 1: 전체 단위 테스트 실행

```bash
./gradlew test --no-daemon
```

예상 결과: 모든 테스트 통과

### Step 2: Docker 환경에서 최종 DoD 검증

아래 검증 항목을 순서대로 실행합니다:

| 검증 항목 | 명령어 | 예상 결과 |
|-----------|--------|-----------|
| 서버 기동 | `docker compose up --build` | `Started GamepaperApplication` 로그 |
| API 빈 목록 | `curl http://localhost:8080/api/games` | `[]` |
| 데이터 삽입 후 조회 | sqlite3 INSERT 후 curl | 게임 데이터 반환 |
| 서버 재시작 후 유지 | `docker compose restart` 후 curl | 동일 데이터 |
| WAL 모드 | `PRAGMA journal_mode;` | `wal` |
| 이미지 서빙 | curl 이미지 URL | HTTP 200 |

### Step 3: 최종 커밋

```bash
git add .
git commit -m "docs: Sprint 1 완료 - 백엔드 인프라 추상화 구현"
git push origin sprint1
```

---

## 리스크 및 주의사항

| 리스크 | 상황 | 대응 방안 |
|--------|------|-----------|
| SQLite 볼륨 마운트 이슈 | `gamepaper.db` 파일이 없으면 Docker가 디렉토리로 마운트 | 첫 실행 전 `touch gamepaper.db` 필수 |
| SQLite in-memory 테스트 격리 | `@DataJpaTest`의 기본 H2 대신 SQLite 사용 시 방언 설정 필요 | `@TestPropertySource`로 SQLite dialect 명시 |
| `LocalStorageService` 프로파일 | `@Profile("local")` 없으면 프로덕션 빌드에서 충돌 | 반드시 어노테이션 확인 |
| Gradle wrapper 권한 | Linux/Mac에서 `./gradlew` 실행 권한 없을 수 있음 | `chmod +x gradlew` 실행 |
| Docker 빌드 캐시 | 의존성 변경 없이 소스만 변경 시 캐시 활용으로 빌드 속도 개선 | `COPY gradlew/build.gradle` 을 소스 COPY보다 먼저 배치 (이미 반영됨) |
| WAL 모드 `@PostConstruct` 타이밍 | JPA가 테이블 생성 전에 PRAGMA 실행될 수 있음 | `DatabaseConfig`의 `@PostConstruct`는 JPA 초기화 후 실행되므로 문제 없음 |

---

## 예상 산출물

Sprint 1 완료 시 다음 산출물이 생성됩니다:

```
D:\work\AI해커톤\
├── build.gradle
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .dockerignore
├── gamepaper.db                          (첫 실행 후 생성)
├── storage/images/                       (이미지 저장 디렉토리)
└── src/
    ├── main/java/com/gamepaper/
    │   ├── GamepaperApplication.java
    │   ├── config/
    │   │   ├── DatabaseConfig.java       (WAL 모드 설정)
    │   │   ├── StorageConfig.java        (정적 파일 서빙)
    │   │   └── WebConfig.java            (CORS 설정)
    │   ├── domain/
    │   │   ├── game/                     (Game 엔티티, Repository, Status)
    │   │   ├── wallpaper/                (Wallpaper 엔티티, Repository)
    │   │   └── crawler/                  (CrawlingLog 엔티티, Repository)
    │   ├── storage/
    │   │   ├── StorageService.java       (인터페이스)
    │   │   └── local/LocalStorageService.java
    │   └── api/
    │       ├── GameApiController.java
    │       ├── WallpaperApiController.java
    │       └── dto/                      (GameDto, WallpaperDto, PagedResponse)
    ├── main/resources/
    │   ├── application.yml
    │   └── application-local.yml
    └── test/java/com/gamepaper/
        ├── domain/game/GameRepositoryTest.java
        ├── storage/LocalStorageServiceTest.java
        └── api/GameApiControllerTest.java
```

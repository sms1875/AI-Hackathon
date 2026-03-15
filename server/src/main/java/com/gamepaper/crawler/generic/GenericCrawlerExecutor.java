package com.gamepaper.crawler.generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamepaper.claude.TaggingService;
import com.gamepaper.crawler.CrawlResult;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * CrawlerStrategy JSON을 읽어 크롤링을 수행하는 범용 실행기.
 *
 * 지원 페이지네이션:
 * - none: 단일 페이지 수집
 * - button_click: "다음 페이지" 버튼 클릭
 * - scroll: 페이지 하단으로 스크롤 (무한 스크롤)
 * - url_pattern: URL 패턴({page})으로 페이지 순회
 *
 * 사용 후 WebDriver 세션은 반드시 종료됩니다 (finally 블록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericCrawlerExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StorageService storageService;
    private final WallpaperRepository wallpaperRepository;
    private final ImageProcessor imageProcessor;
    private final TaggingService taggingService;

    @Value("${selenium.hub-url:http://localhost:4444}")
    private String seleniumHubUrl;

    /**
     * 전략 JSON 문자열을 파싱하여 크롤링을 실행합니다.
     *
     * @param gameId      크롤링 대상 게임 ID
     * @param gameUrl     크롤링 시작 URL
     * @param strategyJson 파싱 전략 JSON 문자열
     * @return 수집 결과 (성공/실패, 수집 개수)
     */
    public CrawlResult execute(Long gameId, String gameUrl, String strategyJson) {
        StrategyDto strategy;
        try {
            strategy = MAPPER.readValue(strategyJson, StrategyDto.class);
        } catch (Exception e) {
            log.error("전략 JSON 파싱 실패 - gameId={}", gameId, e);
            return CrawlResult.failure("전략 JSON 파싱 실패: " + e.getMessage());
        }

        if (strategy.getImageSelector() == null || strategy.getImageSelector().isBlank()) {
            return CrawlResult.failure("imageSelector가 비어 있습니다.");
        }

        log.info("GenericCrawlerExecutor 실행 - gameId={}, paginationType={}",
                gameId, strategy.getPaginationType());

        WebDriver driver = null;
        try {
            driver = createDriver(strategy.getTimeoutSeconds());
            return crawl(driver, gameId, gameUrl, strategy);
        } catch (Exception e) {
            log.error("크롤링 실행 오류 - gameId={}", gameId, e);
            return CrawlResult.failure(e.getMessage());
        } finally {
            // 세션 반드시 종료 (메모리 누수 방지)
            quitDriver(driver);
        }
    }

    private CrawlResult crawl(WebDriver driver, Long gameId,
                               String gameUrl, StrategyDto strategy) {
        int totalCount = 0;
        int consecutiveDuplicates = 0;
        int duplicateThreshold = parseDuplicateThreshold(strategy.getStopCondition());

        // 사전 동작 실행 (팝업 닫기 등)
        driver.get(gameUrl);
        sleep(strategy.getWaitMs());
        executePreActions(driver, strategy.getPreActions());

        String pagination = strategy.getPaginationType() != null
                ? strategy.getPaginationType() : "none";

        switch (pagination) {
            case "scroll" -> {
                for (int i = 0; i < strategy.getMaxPages(); i++) {
                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    if (newCount == 0) consecutiveDuplicates++;
                    else consecutiveDuplicates = 0;

                    if (duplicateThreshold > 0 && consecutiveDuplicates >= duplicateThreshold) {
                        log.info("중단 조건 충족 - consecutiveDuplicates={}", consecutiveDuplicates);
                        break;
                    }

                    ((JavascriptExecutor) driver)
                            .executeScript("window.scrollTo(0, document.body.scrollHeight)");
                    sleep(strategy.getWaitMs());
                }
            }
            case "button_click" -> {
                for (int page = 0; page < strategy.getMaxPages(); page++) {
                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    // 다음 버튼 찾기
                    if (strategy.getNextButtonSelector() == null) break;
                    try {
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        WebElement nextBtn = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.cssSelector(strategy.getNextButtonSelector())));
                        nextBtn.click();
                        sleep(strategy.getWaitMs());
                    } catch (Exception e) {
                        log.debug("다음 버튼 없음 - 크롤링 종료 (page={})", page);
                        break;
                    }
                }
            }
            case "url_pattern" -> {
                if (strategy.getUrlPattern() == null) break;
                for (int page = 1; page <= strategy.getMaxPages(); page++) {
                    String url = strategy.getUrlPattern().replace("{page}", String.valueOf(page));
                    driver.get(url);
                    sleep(strategy.getWaitMs());

                    int newCount = collectImages(driver, gameId, strategy);
                    totalCount += newCount;

                    if (newCount == 0) {
                        log.debug("이미지 없음 - 크롤링 종료 (page={})", page);
                        break;
                    }
                }
            }
            default -> {
                // none: 단일 페이지
                totalCount = collectImages(driver, gameId, strategy);
            }
        }

        log.info("GenericCrawlerExecutor 완료 - gameId={}, 총 수집={}", gameId, totalCount);
        return CrawlResult.success(totalCount);
    }

    /**
     * 현재 페이지에서 이미지를 수집하여 저장합니다.
     *
     * @return 신규 저장된 이미지 수
     */
    private int collectImages(WebDriver driver, Long gameId, StrategyDto strategy) {
        int count = 0;
        try {
            List<WebElement> elements = driver.findElements(
                    By.cssSelector(strategy.getImageSelector()));

            List<String> urls = new ArrayList<>();
            for (WebElement el : elements) {
                String attr = strategy.getImageAttribute() != null
                        ? strategy.getImageAttribute() : "src";
                String url = el.getAttribute(attr);
                if (url == null || url.isBlank()) continue;
                if (!urls.contains(url)) urls.add(url);
            }

            for (String imageUrl : urls) {
                if (downloadAndSave(gameId, imageUrl)) {
                    count++;
                }
                sleep(500);  // 요청 간 딜레이
            }
        } catch (Exception e) {
            log.warn("이미지 수집 중 오류 - gameId={}, 오류={}", gameId, e.getMessage());
        }
        return count;
    }

    /**
     * 사전 동작(preActions) 실행.
     * 지원 action: click, wait
     */
    private void executePreActions(WebDriver driver, List<Map<String, String>> preActions) {
        if (preActions == null || preActions.isEmpty()) return;

        for (Map<String, String> action : preActions) {
            String type = action.getOrDefault("action", "");
            String selector = action.get("selector");

            try {
                switch (type) {
                    case "click" -> {
                        if (selector != null) {
                            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                            WebElement el = wait.until(
                                    ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                            el.click();
                            log.debug("preAction click 실행 - selector={}", selector);
                        }
                    }
                    case "wait" -> {
                        int ms = Integer.parseInt(action.getOrDefault("ms", "1000"));
                        sleep(ms);
                    }
                    default -> log.warn("알 수 없는 preAction 타입: {}", type);
                }
            } catch (Exception e) {
                // preAction 실패는 무시하고 계속 진행 (팝업이 없는 경우 등)
                log.debug("preAction 실행 실패 (무시) - action={}, 오류={}", type, e.getMessage());
            }
        }
    }

    /**
     * stopCondition 파싱.
     * "duplicate_count:10" → 10 반환
     */
    private int parseDuplicateThreshold(String stopCondition) {
        if (stopCondition == null || !stopCondition.startsWith("duplicate_count:")) return 0;
        try {
            return Integer.parseInt(stopCondition.split(":")[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 이미지 URL 다운로드 후 저장. AbstractGameCrawler.downloadAndSave()와 동일한 로직.
     */
    private boolean downloadAndSave(Long gameId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        String ext = imageProcessor.extractExtension(imageUrl);
        String urlHash = String.valueOf(Math.abs(imageUrl.hashCode()));
        String fileName = urlHash + "." + ext;

        if (wallpaperRepository.existsByGameIdAndFileName(gameId, fileName)) {
            log.debug("중복 이미지 건너뜀 - gameId={}, fileName={}", gameId, fileName);
            return false;
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes.length == 0) return false;

            com.gamepaper.crawler.image.ImageMetadata metadata =
                    imageProcessor.process(imageBytes, ext);
            String savedUrl = storageService.upload(gameId, fileName, imageBytes);

            com.gamepaper.domain.wallpaper.Wallpaper wallpaper =
                    new com.gamepaper.domain.wallpaper.Wallpaper(gameId, fileName, savedUrl);
            wallpaper.setWidth(metadata.getWidth());
            wallpaper.setHeight(metadata.getHeight());
            wallpaper.setBlurHash(metadata.getBlurHash());

            // 태그 생성 (비동기 처리 없이 순차 실행 - 이미 크롤러가 별도 스레드에서 실행 중)
            try {
                List<String> tags = taggingService.generateTags(imageBytes, ext);
                if (!tags.isEmpty()) {
                    wallpaper.setTags(taggingService.toJsonString(tags));
                }
            } catch (Exception e) {
                log.debug("태그 생성 건너뜀 (크롤링 계속 진행) - 오류={}", e.getMessage());
            }

            wallpaperRepository.save(wallpaper);
            log.debug("이미지 저장 완료 - gameId={}, url={}", gameId, imageUrl);
            return true;

        } catch (Exception e) {
            log.warn("이미지 저장 실패 - url={}, 오류={}", imageUrl, e.getMessage());
            return false;
        }
    }

    private byte[] downloadImage(String imageUrl)
            throws java.io.IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "Mozilla/5.0 (compatible; GamePaper-Crawler/1.0)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        java.net.http.HttpResponse<byte[]> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.warn("이미지 다운로드 실패 - status={}, url={}", response.statusCode(), imageUrl);
            return new byte[0];
        }
        return response.body();
    }

    private WebDriver createDriver(int timeoutSeconds) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );
        try {
            RemoteWebDriver driver = new RemoteWebDriver(
                    URI.create(seleniumHubUrl + "/wd/hub").toURL(), options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            return driver;
        } catch (Exception e) {
            throw new RuntimeException("Selenium 드라이버 생성 실패: " + seleniumHubUrl, e);
        }
    }

    private void quitDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("WebDriver 종료 실패: {}", e.getMessage());
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

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

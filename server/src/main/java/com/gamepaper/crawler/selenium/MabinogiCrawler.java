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

            // 배경화면 이미지 요소 추출 (사이트 구조에 따라 selector 조정 필요)
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

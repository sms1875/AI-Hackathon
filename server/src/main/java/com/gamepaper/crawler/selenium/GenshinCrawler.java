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

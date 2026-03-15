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
     * RemoteWebDriver 세션 생성.
     * 반드시 사용 후 driver.quit() 호출 필요.
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

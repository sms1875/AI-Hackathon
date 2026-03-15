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

            // 배경화면 이미지 URL 추출
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

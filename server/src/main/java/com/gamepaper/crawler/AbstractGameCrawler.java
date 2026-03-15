package com.gamepaper.crawler;

import com.gamepaper.crawler.image.ImageMetadata;
import com.gamepaper.crawler.image.ImageProcessor;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
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
        // URL 해시 기반 파일명으로 중복 체크 (재실행 시 동일 이미지 중복 저장 방지)
        String urlHash = String.valueOf(Math.abs(imageUrl.hashCode()));
        String fileName = urlHash + "." + ext;

        // 중복 체크
        if (wallpaperRepository.existsByGameIdAndFileName(gameId, fileName)) {
            log.debug("중복 이미지 건너뜀 - gameId={}, fileName={}", gameId, fileName);
            return false;
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes.length == 0) return false;

            ImageMetadata metadata = imageProcessor.process(imageBytes, ext);
            String savedUrl = storageService.upload(gameId, fileName, imageBytes);

            Wallpaper wallpaper = new Wallpaper(gameId, fileName, savedUrl);
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

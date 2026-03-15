package com.gamepaper.claude;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

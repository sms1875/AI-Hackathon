package com.gamepaper.api;

import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.domain.wallpaper.Wallpaper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 배경화면 추천 API 컨트롤러.
 * I-3 해소: WallpaperApiController에서 추천 관련 엔드포인트를 분리.
 */
@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperRecommendApiController {

    private final RecommendationService recommendationService;

    /**
     * 추천 배경화면 API.
     * device-id 기반으로 좋아요 이력을 분석하여 유사 배경화면을 반환합니다.
     *
     * @param deviceId 기기 ID (device-id 헤더)
     * @return 추천 배경화면 목록 (좋아요 이력 없으면 빈 배열)
     */
    @GetMapping("/recommended")
    public List<WallpaperDto> getRecommended(
            @RequestHeader(value = "device-id", required = false) String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }

        List<Wallpaper> recommendations = recommendationService.recommend(deviceId);
        return recommendations.stream().map(WallpaperDto::new).collect(Collectors.toList());
    }
}

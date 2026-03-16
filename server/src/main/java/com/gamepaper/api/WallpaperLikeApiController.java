package com.gamepaper.api;

import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 배경화면 좋아요 토글 API 컨트롤러.
 * I-3 해소: WallpaperApiController에서 좋아요 관련 엔드포인트를 분리.
 */
@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperLikeApiController {

    private final WallpaperRepository wallpaperRepository;
    private final UserLikeRepository userLikeRepository;

    /**
     * 좋아요 토글 API.
     * device-id 헤더로 사용자를 식별합니다.
     *
     * @param id       배경화면 ID
     * @param deviceId 기기 ID (device-id 헤더)
     * @return {"liked": true/false, "likeCount": N}
     */
    @Transactional
    @PostMapping("/{id}/like")
    public Map<String, Object> toggleLike(
            @PathVariable Long id,
            @RequestHeader(value = "device-id", required = false) String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "device-id 헤더가 필요합니다.");
        }

        if (!wallpaperRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "배경화면을 찾을 수 없습니다: " + id);
        }

        boolean alreadyLiked = userLikeRepository.existsByDeviceIdAndWallpaperId(deviceId, id);

        if (alreadyLiked) {
            userLikeRepository.deleteByDeviceIdAndWallpaperId(deviceId, id);
            long likeCount = userLikeRepository.countByWallpaperId(id);
            return Map.of("liked", false, "likeCount", likeCount);
        } else {
            userLikeRepository.save(new UserLike(deviceId, id));
            long likeCount = userLikeRepository.countByWallpaperId(id);
            return Map.of("liked", true, "likeCount", likeCount);
        }
    }
}

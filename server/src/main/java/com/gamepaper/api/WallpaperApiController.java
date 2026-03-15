package com.gamepaper.api;

import com.gamepaper.api.dto.PagedResponse;
import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperApiController {

    private final WallpaperRepository wallpaperRepository;
    private final GameRepository gameRepository;
    private final WallpaperSearchService searchService;
    private final UserLikeRepository userLikeRepository;

    @GetMapping("/{gameId}")
    public PagedResponse<WallpaperDto> getWallpapers(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다: " + gameId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WallpaperDto> result = wallpaperRepository
                .findAllByGameId(gameId, pageable)
                .map(WallpaperDto::new);

        return new PagedResponse<>(result);
    }

    /**
     * 태그 기반 배경화면 검색.
     *
     * @param tags  쉼표로 구분된 태그 목록 (예: dark,landscape)
     * @param mode  검색 방식 - "and" (기본값, 모든 태그 포함) 또는 "or" (하나라도 포함)
     */
    @GetMapping("/search")
    public List<WallpaperDto> searchByTags(
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "and") String mode) {

        if (tags == null || tags.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tags 파라미터가 필요합니다.");
        }

        List<String> tagList = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());

        List<Wallpaper> results = "or".equalsIgnoreCase(mode)
                ? searchService.searchByTagsOr(tagList)
                : searchService.searchByTagsAnd(tagList);

        return results.stream().map(WallpaperDto::new).collect(Collectors.toList());
    }

    /**
     * 좋아요 토글 API.
     * device-id 헤더로 사용자를 식별합니다.
     *
     * @param id       배경화면 ID
     * @param deviceId 기기 ID (device-id 헤더)
     * @return {"liked": true/false, "likeCount": N}
     */
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

package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 좋아요 기반 배경화면 추천 서비스.
 *
 * 추천 로직:
 * 1. device-id의 좋아요 이력에서 wallpaperId 목록 조회
 * 2. 해당 배경화면들의 tags를 파싱하여 태그 빈도 분석
 * 3. 상위 빈도 태그로 OR 검색 → 유사 배경화면 후보 조회
 * 4. 이미 좋아요한 배경화면 제외 후 최대 20개 반환
 *
 * 응답 시간 3초 이내 목표: 인메모리 필터링으로 DB 쿼리 최소화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int MAX_PREFERRED_TAGS = 5;
    private static final int MAX_RECOMMENDATIONS = 20;

    private final UserLikeRepository userLikeRepository;
    private final WallpaperRepository wallpaperRepository;
    private final WallpaperSearchService searchService;

    /**
     * 사용자의 좋아요 이력을 기반으로 추천 배경화면을 반환합니다.
     *
     * @param deviceId 기기 ID
     * @return 추천 배경화면 목록 (최대 20개, 좋아요 이력 없으면 빈 목록)
     */
    public List<Wallpaper> recommend(String deviceId) {
        // 1. 좋아요한 배경화면 ID 조회
        List<Long> likedIds = userLikeRepository.findWallpaperIdsByDeviceId(deviceId);
        if (likedIds.isEmpty()) {
            log.debug("좋아요 이력 없음 - deviceId={}", deviceId);
            return Collections.emptyList();
        }

        Set<Long> likedIdSet = new HashSet<>(likedIds);

        // 2. 좋아요한 배경화면의 태그 빈도 분석
        List<Wallpaper> likedWallpapers = wallpaperRepository.findAllById(likedIds);
        Map<String, Long> tagFrequency = likedWallpapers.stream()
                .filter(wp -> wp.getTags() != null)
                .flatMap(wp -> searchService.parseTagsFromJson(wp.getTags()).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        if (tagFrequency.isEmpty()) {
            log.debug("좋아요한 배경화면에 태그 없음 - deviceId={}", deviceId);
            return Collections.emptyList();
        }

        // 3. 상위 빈도 태그 선택
        List<String> preferredTags = tagFrequency.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(MAX_PREFERRED_TAGS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.debug("선호 태그 - deviceId={}, tags={}", deviceId, preferredTags);

        // 4. OR 검색으로 후보 배경화면 조회
        List<Wallpaper> candidates = searchService.searchByTagsOr(preferredTags);

        // 5. 이미 좋아요한 항목 제외 후 최대 20개 반환
        return candidates.stream()
                .filter(wp -> !likedIdSet.contains(wp.getId()))
                .limit(MAX_RECOMMENDATIONS)
                .collect(Collectors.toList());
    }
}

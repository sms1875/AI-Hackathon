package com.gamepaper.api;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 태그 기반 배경화면 검색 서비스.
 *
 * SQLite의 JSON 함수 지원 제한으로 인메모리 필터링 사용.
 * tags 필드는 JSON 배열 문자열 ("dark","landscape") 형태.
 *
 * AND 검색: 모든 쿼리 태그가 포함된 배경화면만 반환.
 * OR 검색: 쿼리 태그 중 하나라도 포함된 배경화면 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WallpaperSearchService {

    private final WallpaperRepository wallpaperRepository;

    private static final int MAX_RESULTS = 50;

    /**
     * 태그 AND 검색
     *
     * @param tags  검색할 태그 목록
     * @return 모든 태그가 포함된 배경화면 목록 (최대 50개)
     */
    public List<Wallpaper> searchByTagsAnd(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();

        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> containsAllTags(wp.getTags(), tags))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * 태그 OR 검색
     *
     * @param tags  검색할 태그 목록
     * @return 태그 중 하나라도 포함된 배경화면 목록 (최대 50개)
     */
    public List<Wallpaper> searchByTagsOr(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();

        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> containsAnyTag(wp.getTags(), tags))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * 전체 태그 빈도 분석.
     * tags JSON 배열 필드를 파싱하여 태그별 출현 횟수를 계산한다.
     *
     * @return 태그명 → 출현 횟수 맵 (출현 횟수 내림차순 정렬)
     */
    public java.util.Map<String, Long> getTagFrequency() {
        List<Wallpaper> allTagged = wallpaperRepository.findAllTagged();
        return allTagged.stream()
                .filter(wp -> wp.getTags() != null)
                .flatMap(wp -> parseTagsFromJson(wp.getTags()).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        java.util.Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }

    private boolean containsAllTags(String tagsJson, List<String> queryTags) {
        if (tagsJson == null) return false;
        List<String> wallpaperTags = parseTagsFromJson(tagsJson);
        return queryTags.stream().allMatch(wallpaperTags::contains);
    }

    private boolean containsAnyTag(String tagsJson, List<String> queryTags) {
        if (tagsJson == null) return false;
        List<String> wallpaperTags = parseTagsFromJson(tagsJson);
        return queryTags.stream().anyMatch(wallpaperTags::contains);
    }

    /**
     * JSON 배열 문자열에서 태그 목록을 파싱합니다.
     * 예: ["dark","landscape","blue-tone"] → ["dark", "landscape", "blue-tone"]
     */
    public List<String> parseTagsFromJson(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            // 간단한 JSON 배열 파싱 (Jackson 사용)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] arr = mapper.readValue(tagsJson, String[].class);
            return Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("태그 JSON 파싱 실패: {}", tagsJson);
            return Collections.emptyList();
        }
    }
}

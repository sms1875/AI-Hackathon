package com.gamepaper.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 태그 관련 API 컨트롤러.
 * GET /api/tags — 사용 중인 태그 목록 (빈도순)
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagApiController {

    private final WallpaperSearchService searchService;

    /**
     * 사용 중인 태그 목록을 빈도 내림차순으로 반환합니다.
     *
     * 응답 예시:
     * [
     *   {"tag": "dark", "count": 42},
     *   {"tag": "landscape", "count": 38}
     * ]
     */
    @GetMapping
    public List<Map<String, Object>> getTags() {
        Map<String, Long> frequency = searchService.getTagFrequency();
        return frequency.entrySet().stream()
                .map(e -> Map.of("tag", (Object) e.getKey(), "count", (Object) e.getValue()))
                .collect(Collectors.toList());
    }
}

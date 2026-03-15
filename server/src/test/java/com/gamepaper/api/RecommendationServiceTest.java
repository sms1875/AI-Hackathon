package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private UserLikeRepository userLikeRepository;

    @Mock
    private WallpaperRepository wallpaperRepository;

    @Mock
    private WallpaperSearchService searchService;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void recommend_좋아요이력없으면빈목록() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-001"))
                .thenReturn(List.of());

        List<Wallpaper> result = recommendationService.recommend("device-001");

        assertThat(result).isEmpty();
    }

    @Test
    void recommend_좋아요이력있으면유사배경화면반환() {
        // 좋아요한 배경화면 ID 목록
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-001"))
                .thenReturn(List.of(1L, 2L));

        // 좋아요한 배경화면들
        Wallpaper liked1 = new Wallpaper(1L, "a.jpg", "http://example.com/a.jpg");
        liked1.setTags("[\"dark\",\"landscape\"]");
        Wallpaper liked2 = new Wallpaper(2L, "b.jpg", "http://example.com/b.jpg");
        liked2.setTags("[\"dark\",\"character\"]");

        when(wallpaperRepository.findAllById(anyList())).thenReturn(List.of(liked1, liked2));

        // 검색 결과 (dark 태그 기반 OR 검색)
        Wallpaper recommended = new Wallpaper(3L, "c.jpg", "http://example.com/c.jpg");
        recommended.setTags("[\"dark\",\"fantasy\"]");
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(recommended));

        when(searchService.parseTagsFromJson("[\"dark\",\"landscape\"]"))
                .thenReturn(List.of("dark", "landscape"));
        when(searchService.parseTagsFromJson("[\"dark\",\"character\"]"))
                .thenReturn(List.of("dark", "character"));

        List<Wallpaper> result = recommendationService.recommend("device-001");

        assertThat(result).isNotEmpty();
        assertThat(result).doesNotContain(liked1, liked2); // 이미 좋아요한 항목 제외
    }
}

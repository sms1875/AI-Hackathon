package com.gamepaper.api;

import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.BeforeEach;
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

    private Wallpaper likedWallpaper;
    private Wallpaper candidateWallpaper;

    @BeforeEach
    void setUp() {
        likedWallpaper = new Wallpaper(1L, "liked.jpg", "http://example.com/liked.jpg");
        likedWallpaper.setId(1L);
        likedWallpaper.setTags("[\"dark\",\"landscape\"]");

        candidateWallpaper = new Wallpaper(1L, "candidate.jpg", "http://example.com/candidate.jpg");
        candidateWallpaper.setId(2L);
        candidateWallpaper.setTags("[\"dark\",\"city\"]");
    }

    @Test
    void 좋아요이력없으면_빈목록반환() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of());

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).isEmpty();
    }

    @Test
    void 좋아요태그기반_추천반환() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(1L));
        when(wallpaperRepository.findAllById(List.of(1L))).thenReturn(List.of(likedWallpaper));
        when(searchService.parseTagsFromJson("[\"dark\",\"landscape\"]"))
                .thenReturn(List.of("dark", "landscape"));
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(candidateWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void 이미좋아요한항목_추천목록에서제외() {
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(1L));
        when(wallpaperRepository.findAllById(List.of(1L))).thenReturn(List.of(likedWallpaper));
        when(searchService.parseTagsFromJson(anyString())).thenReturn(List.of("dark"));
        // 후보에 이미 좋아요한 wallpaper(id=1)가 포함
        when(searchService.searchByTagsOr(anyList())).thenReturn(List.of(likedWallpaper, candidateWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L); // id=1 제외 확인
    }

    @Test
    void 태그없는좋아요이력_빈목록반환() {
        Wallpaper noTagWallpaper = new Wallpaper(1L, "notag.jpg", "http://example.com/notag.jpg");
        noTagWallpaper.setId(3L);
        // tags = null (기본값)
        when(userLikeRepository.findWallpaperIdsByDeviceId("device-1")).thenReturn(List.of(3L));
        when(wallpaperRepository.findAllById(List.of(3L))).thenReturn(List.of(noTagWallpaper));

        List<Wallpaper> result = recommendationService.recommend("device-1");

        assertThat(result).isEmpty();
    }
}

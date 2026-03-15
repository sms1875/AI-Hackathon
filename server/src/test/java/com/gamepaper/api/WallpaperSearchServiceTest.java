package com.gamepaper.api;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WallpaperSearchServiceTest {

    @Mock
    private WallpaperRepository wallpaperRepository;

    @InjectMocks
    private WallpaperSearchService service;

    private Wallpaper wpDarkLandscape;
    private Wallpaper wpDarkCity;
    private Wallpaper wpBright;

    @BeforeEach
    void setUp() {
        // Wallpaper(gameId, fileName, url) 생성자 사용 후 id 직접 설정
        wpDarkLandscape = new Wallpaper(1L, "img1.jpg", "http://example.com/img1.jpg");
        wpDarkLandscape.setId(1L);
        wpDarkLandscape.setTags("[\"dark\",\"landscape\"]");

        wpDarkCity = new Wallpaper(1L, "img2.jpg", "http://example.com/img2.jpg");
        wpDarkCity.setId(2L);
        wpDarkCity.setTags("[\"dark\",\"city\"]");

        wpBright = new Wallpaper(1L, "img3.jpg", "http://example.com/img3.jpg");
        wpBright.setId(3L);
        wpBright.setTags("[\"bright\",\"landscape\"]");
    }

    @Test
    void AND검색_모든태그포함() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        List<Wallpaper> result = service.searchByTagsAnd(List.of("dark", "landscape"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void OR검색_하나라도포함() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        List<Wallpaper> result = service.searchByTagsOr(List.of("city", "bright"));

        assertThat(result).hasSize(2);
    }

    @Test
    void 빈태그목록_빈결과반환() {
        assertThat(service.searchByTagsAnd(List.of())).isEmpty();
        assertThat(service.searchByTagsOr(List.of())).isEmpty();
    }

    @Test
    void NULL태그목록_빈결과반환() {
        assertThat(service.searchByTagsAnd(null)).isEmpty();
    }

    @Test
    void parseTagsFromJson_정상파싱() {
        List<String> tags = service.parseTagsFromJson("[\"dark\",\"landscape\",\"blue-tone\"]");
        assertThat(tags).containsExactly("dark", "landscape", "blue-tone");
    }

    @Test
    void parseTagsFromJson_빈문자열_빈목록반환() {
        assertThat(service.parseTagsFromJson("")).isEmpty();
        assertThat(service.parseTagsFromJson(null)).isEmpty();
    }

    @Test
    void parseTagsFromJson_잘못된JSON_빈목록반환() {
        assertThat(service.parseTagsFromJson("not-json")).isEmpty();
    }

    @Test
    void 태그빈도_분석() {
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wpDarkLandscape, wpDarkCity, wpBright)));

        Map<String, Long> freq = service.getTagFrequency();

        assertThat(freq.get("dark")).isEqualTo(2L);
        assertThat(freq.get("landscape")).isEqualTo(2L);
        assertThat(freq.get("city")).isEqualTo(1L);
    }
}

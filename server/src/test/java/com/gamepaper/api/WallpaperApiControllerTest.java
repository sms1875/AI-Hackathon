package com.gamepaper.api;

import com.gamepaper.config.DataInitializer;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=local",
    "spring.sql.init.mode=never",
    "storage.root=${java.io.tmpdir}/gamepaper-test",
    "storage.base-url=http://localhost:8080"
})
class WallpaperApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private GameRepository gameRepository;

    @MockBean
    private WallpaperSearchService searchService;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void 게임ID로_배경화면_페이지_조회() throws Exception {
        when(gameRepository.existsById(1L)).thenReturn(true);
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setId(1L);
        when(wallpaperRepository.findAllByGameId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wp)));

        mockMvc.perform(get("/api/wallpapers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 존재하지않는_게임ID_404에러() throws Exception {
        when(gameRepository.existsById(99999L)).thenReturn(false);

        mockMvc.perform(get("/api/wallpapers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void 페이지_파라미터_적용() throws Exception {
        when(gameRepository.existsById(1L)).thenReturn(true);
        when(wallpaperRepository.findAllByGameId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/wallpapers/1").param("page", "2").param("size", "6"))
                .andExpect(status().isOk());
    }
}

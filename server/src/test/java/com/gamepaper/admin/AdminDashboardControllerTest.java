package com.gamepaper.admin;

import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameRepository gameRepository;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private CrawlingLogRepository crawlingLogRepository;

    @Test
    @DisplayName("관리자 대시보드 페이지가 200을 반환한다")
    void dashboard_returns200() throws Exception {
        when(gameRepository.count()).thenReturn(6L);
        when(wallpaperRepository.count()).thenReturn(120L);
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.ACTIVE)).thenReturn(Collections.emptyList());
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.UPDATING)).thenReturn(Collections.emptyList());
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.FAILED)).thenReturn(Collections.emptyList());
        when(crawlingLogRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("data"));
    }

    @Test
    @DisplayName("대시보드에 게임/배경화면 통계가 모델에 포함된다")
    void dashboard_containsStats() throws Exception {
        when(gameRepository.count()).thenReturn(6L);
        when(wallpaperRepository.count()).thenReturn(240L);
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.ACTIVE)).thenReturn(Collections.emptyList());
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.UPDATING)).thenReturn(Collections.emptyList());
        when(gameRepository.findAllByStatus(com.gamepaper.domain.game.GameStatus.FAILED)).thenReturn(Collections.emptyList());
        when(crawlingLogRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("data",
                        org.hamcrest.Matchers.hasProperty("totalGames",
                                org.hamcrest.Matchers.equalTo(6L))));
    }
}

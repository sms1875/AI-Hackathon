package com.gamepaper.admin;

import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminGameController.class)
class AdminGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameRepository gameRepository;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private CrawlingLogRepository crawlingLogRepository;

    @MockBean
    private CrawlerStrategyRepository strategyRepository;

    @Test
    @DisplayName("게임 목록 페이지가 200을 반환한다")
    void gameList_returns200() throws Exception {
        when(gameRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/games"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/game-list"))
                .andExpect(model().attributeExists("games"));
    }

    @Test
    @DisplayName("게임 목록에 게임 항목이 포함된다")
    void gameList_containsItems() throws Exception {
        Game game = new Game("원신", "https://genshin.hoyoverse.com");
        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(wallpaperRepository.countByGameId(game.getId())).thenReturn(10L);

        mockMvc.perform(get("/admin/games"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("games"));
    }

    @Test
    @DisplayName("게임 등록 폼 페이지가 200을 반환한다")
    void newGameForm_returns200() throws Exception {
        mockMvc.perform(get("/admin/games/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/game-new"))
                .andExpect(model().attributeExists("game"));
    }

    @Test
    @DisplayName("존재하는 게임 상세 페이지가 200을 반환한다")
    void gameDetail_returns200() throws Exception {
        Game game = new Game("원신", "https://genshin.hoyoverse.com");
        when(gameRepository.findById(anyLong())).thenReturn(Optional.of(game));
        when(wallpaperRepository.findAllByGameId(anyLong(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(crawlingLogRepository.findAllByGameIdOrderByStartedAtDesc(anyLong())).thenReturn(Collections.emptyList());
        when(strategyRepository.findAllByGameIdOrderByVersionDesc(anyLong())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/games/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/game-detail"))
                .andExpect(model().attributeExists("game"));
    }

    @Test
    @DisplayName("존재하지 않는 게임 상세 페이지는 404를 반환한다")
    void gameDetail_notFound_returns404() throws Exception {
        when(gameRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/games/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("게임명과 URL 없이 등록하면 400을 반환한다")
    void createGame_missingParams_returns400() throws Exception {
        mockMvc.perform(post("/admin/games/new")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "")
                        .param("url", ""))
                .andExpect(status().isBadRequest());
    }
}

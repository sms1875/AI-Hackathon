package com.gamepaper.api;

import com.gamepaper.config.DataInitializer;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
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
    "spring.jpa.defer-datasource-initialization=true",
    "spring.profiles.active=local",
    "spring.sql.init.mode=never",
    "spring.sql.init.data-locations=",
    "storage.root=${java.io.tmpdir}/gamepaper-test",
    "storage.base-url=http://localhost:8080"
})
class GameApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    GameRepository gameRepository;

    @MockBean
    WallpaperRepository wallpaperRepository;

    @MockBean
    DataInitializer dataInitializer;

    @Test
    @DisplayName("GET /api/games - 게임 없을 때 빈 목록 반환")
    void getGamesEmpty() throws Exception {
        when(gameRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/games - 게임 등록 후 목록 반환")
    void getGamesWithData() throws Exception {
        Game game = new Game("원신", "https://hoyolab.com");
        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(wallpaperRepository.countByGameId(null)).thenReturn(0L);

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("원신")))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));
    }
}

package com.gamepaper.api;

import com.gamepaper.config.DataInitializer;
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
    "spring.jpa.defer-datasource-initialization=true",
    "spring.profiles.active=local",
    "spring.sql.init.mode=never",
    "spring.sql.init.data-locations=",
    "storage.root=${java.io.tmpdir}/gamepaper-test",
    "storage.base-url=http://localhost:8080"
})
class WallpaperSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void search_단일태그_검색() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags("[\"dark\",\"landscape\"]");
        // I-2 해소 후 findAllTagged(Pageable) 사용
        when(wallpaperRepository.findAllTagged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wp)));

        mockMvc.perform(get("/api/wallpapers/search")
                        .param("tags", "dark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tags").value("[\"dark\",\"landscape\"]"));
    }

    @Test
    void search_태그파라미터없음_400반환() throws Exception {
        mockMvc.perform(get("/api/wallpapers/search"))
                .andExpect(status().isBadRequest());
    }
}

package com.gamepaper.api;

import com.gamepaper.config.DataInitializer;
import com.gamepaper.domain.like.UserLike;
import com.gamepaper.domain.like.UserLikeRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.domain.wallpaper.Wallpaper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class LikeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserLikeRepository userLikeRepository;

    @MockBean
    private WallpaperRepository wallpaperRepository;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void like_새로운좋아요_liked반환() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        when(wallpaperRepository.findById(1L)).thenReturn(Optional.of(wp));
        when(wallpaperRepository.existsById(1L)).thenReturn(true);
        when(userLikeRepository.existsByDeviceIdAndWallpaperId("device-001", 1L)).thenReturn(false);

        mockMvc.perform(post("/api/wallpapers/1/like")
                        .header("device-id", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));

        verify(userLikeRepository, times(1)).save(any(UserLike.class));
    }

    @Test
    void like_이미좋아요한경우_unliked반환() throws Exception {
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        when(wallpaperRepository.findById(1L)).thenReturn(Optional.of(wp));
        when(wallpaperRepository.existsById(1L)).thenReturn(true);
        when(userLikeRepository.existsByDeviceIdAndWallpaperId("device-001", 1L)).thenReturn(true);

        mockMvc.perform(post("/api/wallpapers/1/like")
                        .header("device-id", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));

        verify(userLikeRepository, times(1)).deleteByDeviceIdAndWallpaperId("device-001", 1L);
    }

    @Test
    void like_deviceId헤더없음_400반환() throws Exception {
        mockMvc.perform(post("/api/wallpapers/1/like"))
                .andExpect(status().isBadRequest());
    }
}

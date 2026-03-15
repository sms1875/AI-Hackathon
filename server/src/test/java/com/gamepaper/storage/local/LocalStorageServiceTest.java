package com.gamepaper.storage.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempDir.toString(), "http://localhost:8080");
    }

    @Test
    @DisplayName("파일 업로드 후 URL 반환")
    void uploadReturnsUrl() {
        byte[] data = "test-image".getBytes();
        String url = storageService.upload(1L, "test.jpg", data);

        assertThat(url).isEqualTo("http://localhost:8080/storage/images/1/test.jpg");
    }

    @Test
    @DisplayName("업로드한 파일이 실제로 저장됨")
    void uploadSavesFile() throws IOException {
        byte[] data = "test-image".getBytes();
        storageService.upload(1L, "test.jpg", data);

        Path savedFile = tempDir.resolve("images/1/test.jpg");
        assertThat(Files.exists(savedFile)).isTrue();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(data);
    }

    @Test
    @DisplayName("파일 삭제")
    void deleteRemovesFile() {
        byte[] data = "test-image".getBytes();
        storageService.upload(1L, "test.jpg", data);
        storageService.delete(1L, "test.jpg");

        Path savedFile = tempDir.resolve("images/1/test.jpg");
        assertThat(Files.exists(savedFile)).isFalse();
    }

    @Test
    @DisplayName("getUrl은 파일 존재 여부와 무관하게 URL 반환")
    void getUrlReturnsCorrectUrl() {
        String url = storageService.getUrl(2L, "image.png");
        assertThat(url).isEqualTo("http://localhost:8080/storage/images/2/image.png");
    }

    @Test
    @DisplayName("listFiles — 빈 폴더이면 빈 목록 반환")
    void listFiles_빈폴더_빈목록_반환() {
        java.util.List<String> files = storageService.listFiles(1L);
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("listFiles — 파일 업로드 후 목록 반환")
    void listFiles_파일_업로드_후_목록_반환() {
        storageService.upload(1L, "wallpaper1.jpg", new byte[]{1, 2, 3});
        storageService.upload(1L, "wallpaper2.jpg", new byte[]{4, 5, 6});

        java.util.List<String> files = storageService.listFiles(1L);
        assertThat(files).hasSize(2);
        assertThat(files).containsExactlyInAnyOrder("wallpaper1.jpg", "wallpaper2.jpg");
    }

    @Test
    @DisplayName("listFiles — 존재하지 않는 gameId이면 빈 목록 반환")
    void listFiles_존재하지않는_gameId_빈목록_반환() {
        java.util.List<String> files = storageService.listFiles(999L);
        assertThat(files).isEmpty();
    }
}

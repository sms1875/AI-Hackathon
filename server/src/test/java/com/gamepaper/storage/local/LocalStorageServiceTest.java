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
}

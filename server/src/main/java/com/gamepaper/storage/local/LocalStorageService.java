package com.gamepaper.storage.local;

import com.gamepaper.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@Profile("local")
public class LocalStorageService implements StorageService {

    @Value("${storage.root:/app/storage}")
    private String storageRoot;

    @Value("${storage.base-url:http://localhost:8080}")
    private String baseUrl;

    // 테스트용 생성자
    LocalStorageService(String storageRoot, String baseUrl) {
        this.storageRoot = storageRoot;
        this.baseUrl = baseUrl;
    }

    LocalStorageService() {
    }

    @Override
    public String upload(Long gameId, String fileName, byte[] data) {
        Path dir = Paths.get(storageRoot, "images", String.valueOf(gameId));
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, data);
            log.debug("파일 저장 완료: {}", filePath);
            return getUrl(gameId, fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + fileName, e);
        }
    }

    @Override
    public String getUrl(Long gameId, String fileName) {
        return baseUrl + "/storage/images/" + gameId + "/" + fileName;
    }

    @Override
    public void delete(Long gameId, String fileName) {
        Path filePath = Paths.get(storageRoot, "images", String.valueOf(gameId), fileName);
        try {
            Files.deleteIfExists(filePath);
            log.debug("파일 삭제 완료: {}", filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제 실패: " + fileName, e);
        }
    }

    @Override
    public List<String> listFiles(Long gameId) {
        Path dir = Paths.get(storageRoot, "images", String.valueOf(gameId));
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            log.warn("파일 목록 조회 실패: gameId={}", gameId, e);
            return List.of();
        }
    }
}

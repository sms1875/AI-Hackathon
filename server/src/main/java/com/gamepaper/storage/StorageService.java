package com.gamepaper.storage;

import java.util.List;

public interface StorageService {

    /**
     * 이미지 파일 업로드
     * @return 서빙 가능한 영구 URL
     */
    String upload(Long gameId, String fileName, byte[] data);

    /**
     * 파일의 영구 서빙 URL 반환
     */
    String getUrl(Long gameId, String fileName);

    /**
     * 파일 삭제
     */
    void delete(Long gameId, String fileName);

    /**
     * 게임 폴더 내 파일 목록 (Sprint 3에서 구현)
     */
    List<String> listFiles(Long gameId);
}

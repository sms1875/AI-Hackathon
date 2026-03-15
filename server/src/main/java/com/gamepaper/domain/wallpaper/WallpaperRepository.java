package com.gamepaper.domain.wallpaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WallpaperRepository extends JpaRepository<Wallpaper, Long> {
    Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
    long countByGameId(Long gameId);

    // 중복 저장 방지: 동일 게임 + 파일명이 이미 존재하는지 확인
    boolean existsByGameIdAndFileName(Long gameId, String fileName);

    // 태그 미생성 배경화면 조회 (배치 태깅용)
    List<Wallpaper> findAllByTagsIsNull();

    // 태그 기반 배경화면 검색 (인메모리 필터링)
    // SQLite의 JSON 함수 제한으로 인해 tags IS NOT NULL 인 전체 목록을 Java에서 필터링
    @Query("SELECT w FROM Wallpaper w WHERE w.tags IS NOT NULL ORDER BY w.createdAt DESC")
    List<Wallpaper> findAllTagged();
}

package com.gamepaper.domain.wallpaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WallpaperRepository extends JpaRepository<Wallpaper, Long> {
    Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
    long countByGameId(Long gameId);

    // 중복 저장 방지: 동일 게임 + 파일명이 이미 존재하는지 확인
    boolean existsByGameIdAndFileName(Long gameId, String fileName);
}

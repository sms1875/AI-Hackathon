package com.gamepaper.domain.wallpaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WallpaperRepository extends JpaRepository<Wallpaper, Long> {
    Page<Wallpaper> findAllByGameId(Long gameId, Pageable pageable);
    long countByGameId(Long gameId);
}

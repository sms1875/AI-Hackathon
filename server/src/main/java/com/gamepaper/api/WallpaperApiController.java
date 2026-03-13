package com.gamepaper.api;

import com.gamepaper.api.dto.PagedResponse;
import com.gamepaper.api.dto.WallpaperDto;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/wallpapers")
@RequiredArgsConstructor
public class WallpaperApiController {

    private final WallpaperRepository wallpaperRepository;
    private final GameRepository gameRepository;

    @GetMapping("/{gameId}")
    public PagedResponse<WallpaperDto> getWallpapers(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다: " + gameId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WallpaperDto> result = wallpaperRepository
                .findAllByGameId(gameId, pageable)
                .map(WallpaperDto::new);

        return new PagedResponse<>(result);
    }
}

package com.gamepaper.api.dto;

import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GameDto {

    private final Long id;
    private final String name;
    private final long wallpaperCount;
    private final GameStatus status;
    private final LocalDateTime lastCrawledAt;

    public GameDto(Game game, long wallpaperCount) {
        this.id = game.getId();
        this.name = game.getName();
        this.wallpaperCount = wallpaperCount;
        this.status = game.getStatus();
        this.lastCrawledAt = game.getLastCrawledAt();
    }
}

package com.gamepaper.admin.dto;

import com.gamepaper.domain.game.Game;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GameListItem {
    private final Long id;
    private final String name;
    private final String url;
    private final String status;       // ACTIVE / UPDATING / FAILED
    private final long wallpaperCount;
    private final LocalDateTime lastCrawledAt;

    public GameListItem(Game game, long wallpaperCount) {
        this.id = game.getId();
        this.name = game.getName();
        this.url = game.getUrl();
        this.status = game.getStatus().name();
        this.wallpaperCount = wallpaperCount;
        this.lastCrawledAt = game.getLastCrawledAt();
    }
}

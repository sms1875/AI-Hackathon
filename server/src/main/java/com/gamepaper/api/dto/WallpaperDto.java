package com.gamepaper.api.dto;

import com.gamepaper.domain.wallpaper.Wallpaper;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WallpaperDto {

    private final Long id;
    private final String url;
    private final Integer width;
    private final Integer height;
    private final String blurHash;
    private final String tags;
    private final String description;
    private final String imageType;
    private final LocalDateTime createdAt;

    public WallpaperDto(Wallpaper wallpaper) {
        this.id = wallpaper.getId();
        this.url = wallpaper.getUrl();
        this.width = wallpaper.getWidth();
        this.height = wallpaper.getHeight();
        this.blurHash = wallpaper.getBlurHash();
        this.tags = wallpaper.getTags();
        this.description = wallpaper.getDescription();
        this.imageType = wallpaper.getImageType();
        this.createdAt = wallpaper.getCreatedAt();
    }
}

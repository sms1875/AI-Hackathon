package com.gamepaper.domain.wallpaper;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallpapers")
@Getter
@Setter
@NoArgsConstructor
public class Wallpaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(length = 500)
    private String url;

    private Integer width;

    private Integer height;

    @Column(name = "blur_hash", length = 100)
    private String blurHash;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_type", length = 20)
    private String imageType = "mobile";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Wallpaper(Long gameId, String fileName, String url) {
        this.gameId = gameId;
        this.fileName = fileName;
        this.url = url;
    }
}

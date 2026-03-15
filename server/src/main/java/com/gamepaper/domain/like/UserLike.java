package com.gamepaper.domain.like;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 좋아요 엔티티.
 *
 * 사용자 식별: device-id 헤더 (기기 고유 ID, 앱 재설치 시 초기화됨).
 * 서버에 최소 정보만 저장: deviceId + wallpaperId + 시각.
 *
 * 복합 유니크 제약: (deviceId, wallpaperId) — 중복 좋아요 방지.
 */
@Entity
@Table(name = "user_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "wallpaper_id"}))
@Getter
@NoArgsConstructor
public class UserLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "wallpaper_id", nullable = false)
    private Long wallpaperId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public UserLike(String deviceId, Long wallpaperId) {
        this.deviceId = deviceId;
        this.wallpaperId = wallpaperId;
    }
}

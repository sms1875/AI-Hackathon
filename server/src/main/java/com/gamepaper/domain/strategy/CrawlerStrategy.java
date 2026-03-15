package com.gamepaper.domain.strategy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_strategies")
@Getter
@Setter
@NoArgsConstructor
public class CrawlerStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "strategy_json", columnDefinition = "TEXT", nullable = false)
    private String strategyJson;

    // 버전 번호: 재분석 시 1씩 증가
    @Column(nullable = false)
    private Integer version = 1;

    // AI 분석 완료 시각
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public CrawlerStrategy(Long gameId, String strategyJson) {
        this.gameId = gameId;
        this.strategyJson = strategyJson;
        this.version = 1;
        this.analyzedAt = LocalDateTime.now();
    }
}

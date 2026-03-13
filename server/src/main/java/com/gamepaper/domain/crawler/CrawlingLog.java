package com.gamepaper.domain.crawler;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawling_logs")
@Getter
@Setter
@NoArgsConstructor
public class CrawlingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "collected_count")
    private Integer collectedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CrawlingLogStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

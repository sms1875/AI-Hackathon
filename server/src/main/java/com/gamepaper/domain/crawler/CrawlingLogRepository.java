package com.gamepaper.domain.crawler;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrawlingLogRepository extends JpaRepository<CrawlingLog, Long> {
    List<CrawlingLog> findTop10ByOrderByStartedAtDesc();
    List<CrawlingLog> findAllByGameIdOrderByStartedAtDesc(Long gameId);
}

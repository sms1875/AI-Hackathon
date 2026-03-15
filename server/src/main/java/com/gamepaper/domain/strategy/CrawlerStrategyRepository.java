package com.gamepaper.domain.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CrawlerStrategyRepository extends JpaRepository<CrawlerStrategy, Long> {

    // 최신 버전 전략 조회
    Optional<CrawlerStrategy> findTopByGameIdOrderByVersionDesc(Long gameId);

    // 전략 이력 전체 (버전 내림차순)
    List<CrawlerStrategy> findAllByGameIdOrderByVersionDesc(Long gameId);
}

package com.gamepaper.crawler;

/**
 * 게임 크롤러 인터페이스.
 * Sprint 4에서 GenericCrawlerExecutor로 교체 시 CrawlerScheduler는 이 인터페이스를 그대로 사용.
 */
public interface GameCrawler {

    /**
     * 크롤러가 담당하는 게임 ID (games 테이블의 id)
     */
    Long getGameId();

    /**
     * 크롤링 실행
     */
    CrawlResult crawl();
}

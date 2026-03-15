package com.gamepaper.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void enableWalMode() {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
    }
}

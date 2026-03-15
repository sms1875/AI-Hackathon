package com.gamepaper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GamepaperApplication {
    public static void main(String[] args) {
        SpringApplication.run(GamepaperApplication.class, args);
    }
}

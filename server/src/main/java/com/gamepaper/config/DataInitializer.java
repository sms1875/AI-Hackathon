package com.gamepaper.config;

import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 시작 시 기존 6개 게임 초기 데이터 등록.
 * 이미 같은 URL이 존재하면 건너뜁니다 (멱등성 보장).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final GameRepository gameRepository;

    // 게임명 → URL 매핑 (각 크롤러 클래스의 TARGET_URL 상수와 동일)
    private static final List<String[]> INITIAL_GAMES = List.of(
            new String[]{"원신", "https://www.hoyolab.com/wallpaper"},
            new String[]{"마비노기", "https://mabinogi.nexon.com/web/contents/wallPaper"},
            new String[]{"메이플스토리 모바일", "https://m.maplestory.nexon.com/Media/WallPaper"},
            new String[]{"NIKKE", "https://www.nikke-kr.com/fansite.html"},
            new String[]{"파이널판타지 XIV", "https://na.finalfantasyxiv.com/lodestone/special/fankit/desktop_wallpaper/"},
            new String[]{"검은사막", "https://www.kr.playblackdesert.com/ko-KR/About/WallPaper"}
    );

    @Override
    public void run(String... args) {
        for (String[] gameInfo : INITIAL_GAMES) {
            String name = gameInfo[0];
            String url = gameInfo[1];

            if (!gameRepository.existsByUrl(url)) {
                Game game = new Game(name, url);
                gameRepository.save(game);
                log.info("초기 게임 등록 완료 - name={}, url={}", name, url);
            } else {
                log.debug("게임 이미 존재 - 건너뜀: {}", name);
            }
        }
    }
}

package com.gamepaper.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("게임 저장 및 조회")
    void saveAndFind() {
        Game game = new Game("원신", "https://hoyolab.com");
        Game saved = gameRepository.save(game);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("원신");
        assertThat(saved.getStatus()).isEqualTo(GameStatus.ACTIVE);
    }

    @Test
    @DisplayName("상태별 게임 목록 조회")
    void findByStatus() {
        gameRepository.save(new Game("원신", "https://hoyolab.com"));
        Game failedGame = new Game("NIKKE", "https://nikke-kr.com");
        failedGame.setStatus(GameStatus.FAILED);
        gameRepository.save(failedGame);

        List<Game> activeGames = gameRepository.findAllByStatus(GameStatus.ACTIVE);
        assertThat(activeGames).hasSize(1);
        assertThat(activeGames.get(0).getName()).isEqualTo("원신");
    }
}

package com.gamepaper.api;

import com.gamepaper.api.dto.GameDto;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameApiController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;

    @GetMapping
    public List<GameDto> getGames() {
        List<Game> games = gameRepository.findAll();
        return games.stream()
                .map(game -> new GameDto(game, wallpaperRepository.countByGameId(game.getId())))
                .collect(Collectors.toList());
    }
}

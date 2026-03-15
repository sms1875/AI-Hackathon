package com.gamepaper.admin;

import com.gamepaper.admin.dto.GameListItem;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.game.Game;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/games")
@RequiredArgsConstructor
public class AdminGameController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;
    private final CrawlingLogRepository crawlingLogRepository;

    // 게임 목록
    @GetMapping
    public String list(Model model) {
        List<GameListItem> items = gameRepository.findAll().stream()
                .map(game -> new GameListItem(game, wallpaperRepository.countByGameId(game.getId())))
                .collect(Collectors.toList());
        model.addAttribute("games", items);
        return "admin/game-list";
    }

    // 게임 등록 폼
    @GetMapping("/new")
    public String newGameForm(Model model) {
        model.addAttribute("game", new Game());
        return "admin/game-new";
    }

    // 게임 등록 처리 (AI 분석 없이 직접 저장)
    @PostMapping("/new")
    public String createGame(@RequestParam String name,
                             @RequestParam String url,
                             RedirectAttributes ra) {
        if (name.isBlank() || url.isBlank()) {
            ra.addFlashAttribute("error", "게임명과 URL을 모두 입력해주세요.");
            return "redirect:/admin/games/new";
        }
        Game game = new Game(name, url);
        gameRepository.save(game);
        ra.addFlashAttribute("message", "게임 '" + name + "'이(가) 등록되었습니다.");
        return "redirect:/admin/games/" + game.getId();
    }

    // 게임 상태 토글 (활성화/비활성화)
    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        gameRepository.findById(id).ifPresent(game -> {
            if (game.getStatus() == com.gamepaper.domain.game.GameStatus.ACTIVE) {
                game.setStatus(com.gamepaper.domain.game.GameStatus.FAILED); // 비활성화는 FAILED로 표시
            } else {
                game.setStatus(com.gamepaper.domain.game.GameStatus.ACTIVE);
            }
            gameRepository.save(game);
        });
        ra.addFlashAttribute("message", "게임 상태가 변경되었습니다.");
        return "redirect:/admin/games";
    }

    // 게임 삭제
    @PostMapping("/{id}/delete")
    public String deleteGame(@PathVariable Long id, RedirectAttributes ra) {
        gameRepository.deleteById(id);
        ra.addFlashAttribute("message", "게임이 삭제되었습니다.");
        return "redirect:/admin/games";
    }

    // 게임 상세 페이지
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다."));

        // 배경화면 목록 (최신순, 최대 100개)
        var pageable = PageRequest.of(0, 100,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var wallpapers = wallpaperRepository.findAllByGameId(id, pageable).getContent();

        // 크롤링 로그
        var logs = crawlingLogRepository.findAllByGameIdOrderByStartedAtDesc(id);

        model.addAttribute("game", game);
        model.addAttribute("wallpapers", wallpapers);
        model.addAttribute("logs", logs);
        return "admin/game-detail";
    }
}

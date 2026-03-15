package com.gamepaper.admin;

import com.gamepaper.admin.dto.DashboardData;
import com.gamepaper.domain.crawler.CrawlingLogRepository;
import com.gamepaper.domain.game.GameRepository;
import com.gamepaper.domain.game.GameStatus;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final GameRepository gameRepository;
    private final WallpaperRepository wallpaperRepository;
    private final CrawlingLogRepository crawlingLogRepository;

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        long totalGames = gameRepository.count();
        long totalWallpapers = wallpaperRepository.count();
        long activeCount = gameRepository.findAllByStatus(GameStatus.ACTIVE).size();
        long updatingCount = gameRepository.findAllByStatus(GameStatus.UPDATING).size();
        long failedCount = gameRepository.findAllByStatus(GameStatus.FAILED).size();

        // 가장 최근 크롤링 완료 시각: 최근 로그 10건 중 finishedAt이 null이 아닌 첫 번째
        LocalDateTime lastCrawledAt = crawlingLogRepository
                .findTop10ByOrderByStartedAtDesc()
                .stream()
                .filter(log -> log.getFinishedAt() != null)
                .map(log -> log.getFinishedAt())
                .findFirst()
                .orElse(null);

        DashboardData data = new DashboardData(
                totalGames, totalWallpapers, lastCrawledAt,
                activeCount, updatingCount, failedCount,
                crawlingLogRepository.findTop10ByOrderByStartedAtDesc()
        );

        model.addAttribute("data", data);
        return "admin/dashboard";
    }
}

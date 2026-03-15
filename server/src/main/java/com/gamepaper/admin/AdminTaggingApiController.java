package com.gamepaper.admin;

import com.gamepaper.claude.BatchTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 배치 태깅 관리자 API.
 * POST /admin/api/tagging/batch — 태그 없는 배경화면 전체 태깅 (비동기)
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/tagging")
@RequiredArgsConstructor
public class AdminTaggingApiController {

    private final BatchTaggingService batchTaggingService;

    /**
     * 태그 없는 배경화면 전체에 일괄 태그 생성을 시작합니다 (비동기).
     * 즉시 202 Accepted 반환 후 백그라운드에서 처리됩니다.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> startBatchTagging() {
        log.info("배치 태깅 시작 요청");
        CompletableFuture.runAsync(batchTaggingService::tagAllUntagged);
        return ResponseEntity.accepted()
                .body(Map.of("message", "배치 태깅이 시작되었습니다. 로그를 통해 진행 상황을 확인하세요."));
    }
}

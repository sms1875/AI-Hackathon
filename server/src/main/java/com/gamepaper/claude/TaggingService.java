package com.gamepaper.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Claude Vision API를 사용하여 이미지에서 태그를 자동 생성하는 서비스.
 *
 * API 키 미설정 또는 예외 발생 시 빈 목록을 반환하여 크롤링 파이프라인을 중단시키지 않는다.
 * 태그 목록을 JSON 배열 문자열로 직렬화하여 Wallpaper.tags 필드에 저장 가능한 형태로 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaggingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeApiClient claudeApiClient;

    /**
     * 이미지에서 태그를 생성합니다.
     *
     * @param imageBytes 이미지 바이트
     * @param extension  파일 확장자 (jpg, png, webp)
     * @return 태그 목록 (실패 시 빈 목록)
     */
    public List<String> generateTags(byte[] imageBytes, String extension) {
        try {
            List<String> tags = claudeApiClient.generateTagsFromImage(imageBytes, extension);
            log.debug("태그 생성 완료 - 태그 수={}", tags.size());
            return tags;
        } catch (IllegalStateException e) {
            log.debug("API 키 미설정 - 태그 생성 건너뜀");
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("태그 생성 실패 (무시): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 태그 목록을 JSON 배열 문자열로 직렬화합니다.
     * Wallpaper.tags 컬럼에 저장할 형태로 변환합니다.
     *
     * @param tags 태그 목록
     * @return JSON 배열 문자열 (예: ["dark","landscape"]) 또는 null (빈 목록)
     */
    public String toJsonString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("태그 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}

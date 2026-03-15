package com.gamepaper.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gamepaper.claude.dto.AnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Claude API 클라이언트.
 * Spring Boot 3.2의 RestClient를 사용합니다.
 * RestClient.Builder 빈 재사용으로 커넥션 풀 공유 (I-2 해소).
 *
 * 사용 전제: ANTHROPIC_API_KEY 환경변수 설정 필요.
 * API 키 미설정 시 IllegalStateException이 발생하며, 호출자가 데모 전략으로 대체합니다.
 */
@Slf4j
@Component
public class ClaudeApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.model:claude-3-5-sonnet-20241022}")
    private String model;

    @Value("${claude.max-tokens:2048}")
    private int maxTokens;

    private final CrawlerStrategyParser parser;
    private final RestClient restClient;  // 빈 재사용 (I-2 해소)

    public ClaudeApiClient(CrawlerStrategyParser parser, RestClient.Builder restClientBuilder) {
        this.parser = parser;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 주어진 HTML 소스를 분석하여 파싱 전략 JSON을 생성합니다.
     *
     * @param pageHtml 대상 페이지 HTML (헤더, 스크립트 태그 제거 권장)
     * @param pageUrl  분석 대상 URL (프롬프트 컨텍스트 제공용)
     * @return 파싱 전략 JSON과 원본 응답 텍스트
     * @throws IllegalStateException API 키가 설정되지 않은 경우
     */
    public AnalyzeResponse analyzeHtml(String pageHtml, String pageUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        String prompt = buildPrompt(pageHtml, pageUrl);
        String responseText = callApi(prompt);
        log.debug("Claude API 응답 수신 - 길이={}", responseText.length());

        JsonNode strategyNode = parser.extractJson(responseText);
        return new AnalyzeResponse(strategyNode, MAPPER.valueToTree(strategyNode).toString());
    }

    private String buildPrompt(String html, String url) {
        // HTML에서 스크립트, 스타일 태그 제거하여 토큰 절약
        String cleanedHtml = html
                .replaceAll("(?s)<script[^>]*>.*?</script>", "")
                .replaceAll("(?s)<style[^>]*>.*?</style>", "");
        // 최대 8000자로 제한 (토큰 비용 절약)
        if (cleanedHtml.length() > 8000) {
            cleanedHtml = cleanedHtml.substring(0, 8000) + "\n... (이하 생략)";
        }

        return """
            당신은 웹 크롤링 전략 전문가입니다.
            아래 HTML을 분석하여 이미지를 수집하기 위한 파싱 전략 JSON을 생성해주세요.

            대상 URL: %s

            HTML:
            %s

            다음 JSON 스키마를 반드시 준수하세요:
            {
              "imageSelector": "이미지를 선택하는 CSS 셀렉터",
              "imageAttribute": "이미지 URL을 추출할 속성 (src, data-src, href 중 하나)",
              "paginationType": "none | button_click | scroll | url_pattern",
              "nextButtonSelector": "다음 페이지 버튼 셀렉터 (button_click일 때)",
              "urlPattern": "페이지 URL 패턴 (url_pattern일 때, {page}를 페이지 번호로)",
              "maxPages": 최대 수집 페이지 수 (숫자),
              "waitMs": 페이지 로딩 대기 시간 ms (숫자),
              "preActions": [
                {"action": "click", "selector": "닫기 버튼 셀렉터"}
              ],
              "stopCondition": "duplicate_count:10 (중복 이미지 N개 연속 시 중단, 선택)"
            }

            JSON만 출력하세요 (```json 블록으로 감싸도 됩니다).
            """.formatted(url, cleanedHtml);
    }

    private String callApi(String prompt) {
        // Anthropic Messages API 요청 바디 구성
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);

        ArrayNode messages = MAPPER.createArrayNode();
        ObjectNode userMessage = MAPPER.createObjectNode();
        userMessage.put("role", "user");

        ArrayNode content = MAPPER.createArrayNode();
        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);

        userMessage.set("content", content);
        messages.add(userMessage);
        requestBody.set("messages", messages);

        String responseBody = restClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        // 응답에서 텍스트 추출
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}

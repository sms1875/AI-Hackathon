package com.gamepaper.claude;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude API 응답 텍스트에서 파싱 전략 JSON을 추출하고 검증합니다.
 */
@Component
public class CrawlerStrategyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // ```json ... ``` 코드 블록 패턴
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.DOTALL);

    /**
     * 응답 텍스트에서 JSON 추출.
     * 1순위: ```json ... ``` 코드 블록
     * 2순위: 텍스트 전체를 JSON으로 시도
     */
    public JsonNode extractJson(String responseText) {
        Matcher m = JSON_BLOCK.matcher(responseText);
        if (m.find()) {
            return parseJson(m.group(1).trim());
        }
        // 코드 블록 없으면 전체 텍스트를 JSON으로 시도
        return parseJson(responseText.trim());
    }

    /**
     * JSON 문자열을 파싱하고 필수 필드를 검증.
     */
    public JsonNode validateAndParse(String jsonText) {
        JsonNode node = parseJson(jsonText);
        if (!node.has("imageSelector")) {
            throw new IllegalArgumentException("파싱 전략에 'imageSelector' 필드가 필요합니다.");
        }
        return node;
    }

    private JsonNode parseJson(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("유효하지 않은 JSON: " + e.getOriginalMessage());
        }
    }
}

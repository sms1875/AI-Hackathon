package com.gamepaper.claude;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CrawlerStrategyParserTest {

    CrawlerStrategyParser parser = new CrawlerStrategyParser();

    @Test
    @DisplayName("```json 코드 블록에서 JSON 추출")
    void extractJson_정상_JSON_블록_파싱() {
        String response = "분석 결과입니다.\n```json\n{\"imageSelector\": \".wallpaper img\"}\n```";
        JsonNode result = parser.extractJson(response);
        assertThat(result.get("imageSelector").asText()).isEqualTo(".wallpaper img");
    }

    @Test
    @DisplayName("코드 블록 없이 직접 JSON 파싱")
    void extractJson_코드블록_없이_직접_JSON() {
        String response = "{\"imageSelector\": \".img\", \"paginationType\": \"button_click\"}";
        JsonNode result = parser.extractJson(response);
        assertThat(result.get("paginationType").asText()).isEqualTo("button_click");
    }

    @Test
    @DisplayName("JSON 없으면 IllegalArgumentException 발생")
    void extractJson_JSON_없으면_예외() {
        assertThatThrownBy(() -> parser.extractJson("JSON이 없는 텍스트"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("imageSelector 필드 누락 시 IllegalArgumentException 발생")
    void validate_필수필드_누락시_예외() {
        String json = "{\"paginationType\": \"button_click\"}"; // imageSelector 누락
        assertThatThrownBy(() -> parser.validateAndParse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageSelector");
    }
}

package com.gamepaper.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gamepaper.claude.ClaudeApiClient;
import com.gamepaper.claude.HtmlFetcher;
import com.gamepaper.claude.dto.AnalyzeResponse;
import com.gamepaper.domain.strategy.CrawlerStrategyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyzeApiController.class)
class AdminAnalyzeApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ClaudeApiClient claudeApiClient;
    @MockBean HtmlFetcher htmlFetcher;
    @MockBean CrawlerStrategyRepository strategyRepository;

    @Test
    @DisplayName("정상 요청 시 전략 JSON 반환")
    void analyze_정상요청_전략JSON_반환() throws Exception {
        // Mock HTML 수집
        when(htmlFetcher.fetch(eq("https://example.com"))).thenReturn("<html><body>test</body></html>");

        // Mock 파싱 전략 JSON
        ObjectNode strategyNode = objectMapper.createObjectNode();
        strategyNode.put("imageSelector", ".wallpaper img");
        strategyNode.put("paginationType", "button_click");
        AnalyzeResponse mockResponse = new AnalyzeResponse(strategyNode, strategyNode.toString());

        when(claudeApiClient.analyzeHtml(anyString(), eq("https://example.com")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/admin/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy.imageSelector").value(".wallpaper img"));
    }

    @Test
    @DisplayName("URL 누락 시 400 Bad Request 반환")
    void analyze_URL_누락시_400() throws Exception {
        mockMvc.perform(post("/admin/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}

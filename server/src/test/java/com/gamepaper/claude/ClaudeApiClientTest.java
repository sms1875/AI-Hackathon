package com.gamepaper.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ClaudeApiClient 단위 테스트.
 * 실제 API 호출 없이 API 키 미설정 예외 처리 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ClaudeApiClientTest {

    @Mock
    private CrawlerStrategyParser parser;

    @Test
    void API키_미설정시_analyzeHtml_예외발생() {
        org.springframework.web.client.RestClient.Builder builder =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.Builder.class);
        org.springframework.web.client.RestClient mockRestClient =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.class);
        org.mockito.Mockito.when(builder.build()).thenReturn(mockRestClient);

        ClaudeApiClient client = new ClaudeApiClient(parser, builder);
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.analyzeHtml("<html/>", "https://example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void API키_미설정시_generateTagsFromImage_예외발생() {
        org.springframework.web.client.RestClient.Builder builder =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.Builder.class);
        org.springframework.web.client.RestClient mockRestClient =
                org.mockito.Mockito.mock(org.springframework.web.client.RestClient.class);
        org.mockito.Mockito.when(builder.build()).thenReturn(mockRestClient);

        ClaudeApiClient client = new ClaudeApiClient(parser, builder);
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.generateTagsFromImage(new byte[0], "jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}

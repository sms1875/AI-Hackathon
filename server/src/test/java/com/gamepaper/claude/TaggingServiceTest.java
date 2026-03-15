package com.gamepaper.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaggingServiceTest {

    @Mock
    private ClaudeApiClient claudeApiClient;

    @InjectMocks
    private TaggingService taggingService;

    @Test
    void generateTags_정상응답시_태그목록반환() {
        // given
        when(claudeApiClient.generateTagsFromImage(any(byte[].class), anyString()))
                .thenReturn(List.of("dark", "landscape", "blue-tone"));

        // when
        List<String> tags = taggingService.generateTags(new byte[]{1, 2, 3}, "jpg");

        // then
        assertThat(tags).containsExactly("dark", "landscape", "blue-tone");
    }

    @Test
    void generateTags_예외발생시_빈목록반환() {
        // given
        when(claudeApiClient.generateTagsFromImage(any(byte[].class), anyString()))
                .thenThrow(new RuntimeException("API 에러"));

        // when
        List<String> tags = taggingService.generateTags(new byte[]{1, 2, 3}, "jpg");

        // then
        assertThat(tags).isEmpty();
    }
}

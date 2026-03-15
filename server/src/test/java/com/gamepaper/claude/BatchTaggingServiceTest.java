package com.gamepaper.claude;

import com.gamepaper.domain.wallpaper.Wallpaper;
import com.gamepaper.domain.wallpaper.WallpaperRepository;
import com.gamepaper.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchTaggingServiceTest {

    @Mock
    private WallpaperRepository wallpaperRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private TaggingService taggingService;

    @InjectMocks
    private BatchTaggingService batchTaggingService;

    @Test
    void tagAllUntagged_태그없는배경화면에태그생성() throws Exception {
        // given
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags(null);
        when(wallpaperRepository.findAllByTagsIsNull()).thenReturn(List.of(wp));
        when(storageService.download(anyLong(), anyString())).thenReturn(new byte[]{1, 2, 3});
        when(taggingService.generateTags(any(), anyString())).thenReturn(List.of("dark", "landscape"));
        when(taggingService.toJsonString(anyList())).thenReturn("[\"dark\",\"landscape\"]");

        // when
        int count = batchTaggingService.tagAllUntagged();

        // then
        verify(wallpaperRepository, times(1)).save(wp);
        assert count == 1;
    }

    @Test
    void tagAllUntagged_저장소오류시건너뜀() throws Exception {
        // given
        Wallpaper wp = new Wallpaper(1L, "test.jpg", "http://example.com/test.jpg");
        wp.setTags(null);
        when(wallpaperRepository.findAllByTagsIsNull()).thenReturn(List.of(wp));
        when(storageService.download(anyLong(), anyString())).thenThrow(new RuntimeException("파일 없음"));

        // when
        int count = batchTaggingService.tagAllUntagged();

        // then
        verify(wallpaperRepository, never()).save(any());
        assert count == 0;
    }
}

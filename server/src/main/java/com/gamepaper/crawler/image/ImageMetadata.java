package com.gamepaper.crawler.image;

import lombok.Builder;
import lombok.Getter;

/**
 * 이미지 처리 결과 (해상도 + BlurHash)
 */
@Getter
@Builder
public class ImageMetadata {

    private final int width;
    private final int height;
    private final String blurHash;
    private final String fileName; // UUID 기반 파일명
}

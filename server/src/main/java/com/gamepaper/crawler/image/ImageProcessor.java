package com.gamepaper.crawler.image;

import io.trbl.blurhash.BlurHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 이미지 메타데이터 처리: 해상도 추출 + BlurHash 생성
 */
@Slf4j
@Component
public class ImageProcessor {

    /**
     * 이미지 바이트 배열로부터 해상도와 BlurHash를 추출합니다.
     *
     * @param imageBytes       이미지 원본 바이트
     * @param originalExtension 확장자 (예: "jpg", "png")
     * @return ImageMetadata (실패 시 기본값으로 반환하여 크롤링 계속 진행)
     */
    public ImageMetadata process(byte[] imageBytes, String originalExtension) {
        String fileName = UUID.randomUUID().toString() + "." + originalExtension;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("이미지 파싱 실패 - 기본 메타데이터로 저장");
                return ImageMetadata.builder()
                        .width(0).height(0).blurHash("").fileName(fileName)
                        .build();
            }

            int width = image.getWidth();
            int height = image.getHeight();
            String blurHash = generateBlurHash(image);

            return ImageMetadata.builder()
                    .width(width)
                    .height(height)
                    .blurHash(blurHash)
                    .fileName(fileName)
                    .build();

        } catch (IOException e) {
            log.warn("이미지 처리 실패: {}", e.getMessage());
            return ImageMetadata.builder()
                    .width(0).height(0).blurHash("").fileName(fileName)
                    .build();
        }
    }

    private String generateBlurHash(BufferedImage image) {
        try {
            return BlurHash.encode(image, 4, 3);
        } catch (Exception e) {
            log.warn("BlurHash 생성 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * URL에서 파일 확장자 추출 (기본값: "jpg")
     */
    public String extractExtension(String url) {
        if (url == null) return "jpg";
        String path = url.split("\\?")[0]; // 쿼리 파라미터 제거
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) return "jpg";
        String ext = path.substring(dotIndex + 1).toLowerCase();
        // 허용 확장자만
        if (ext.matches("jpg|jpeg|png|webp")) return ext;
        return "jpg";
    }
}

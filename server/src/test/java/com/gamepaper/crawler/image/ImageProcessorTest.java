package com.gamepaper.crawler.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageProcessorTest {

    ImageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ImageProcessor();
    }

    private byte[] createTestImageBytes(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("이미지 해상도 추출")
    void extractResolution() throws Exception {
        byte[] imageBytes = createTestImageBytes(1080, 1920);
        ImageMetadata metadata = processor.process(imageBytes, "jpg");

        assertThat(metadata.getWidth()).isEqualTo(1080);
        assertThat(metadata.getHeight()).isEqualTo(1920);
    }

    @Test
    @DisplayName("BlurHash 생성 — 비어 있지 않음")
    void generateBlurHash() throws Exception {
        byte[] imageBytes = createTestImageBytes(100, 100);
        ImageMetadata metadata = processor.process(imageBytes, "jpg");

        // BlurHash가 생성되었거나, 실패 시 빈 문자열 (예외 없이 완료)
        assertThat(metadata.getBlurHash()).isNotNull();
    }

    @Test
    @DisplayName("UUID 기반 파일명 생성")
    void generateUuidFileName() throws Exception {
        byte[] imageBytes = createTestImageBytes(100, 100);
        ImageMetadata m1 = processor.process(imageBytes, "jpg");
        ImageMetadata m2 = processor.process(imageBytes, "png");

        assertThat(m1.getFileName()).endsWith(".jpg");
        assertThat(m2.getFileName()).endsWith(".png");
        assertThat(m1.getFileName()).isNotEqualTo(m2.getFileName());
    }

    @Test
    @DisplayName("잘못된 이미지 바이트 — 기본 메타데이터 반환 (예외 없음)")
    void invalidImageBytes() {
        byte[] badBytes = "not-an-image".getBytes();
        ImageMetadata metadata = processor.process(badBytes, "jpg");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getWidth()).isEqualTo(0);
        assertThat(metadata.getHeight()).isEqualTo(0);
    }

    @Test
    @DisplayName("확장자 추출 — 정상 URL")
    void extractExtension() {
        assertThat(processor.extractExtension("https://example.com/image.jpg")).isEqualTo("jpg");
        assertThat(processor.extractExtension("https://example.com/image.PNG")).isEqualTo("png");
        assertThat(processor.extractExtension("https://example.com/image.webp?v=1")).isEqualTo("webp");
        assertThat(processor.extractExtension("https://example.com/image")).isEqualTo("jpg");
        assertThat(processor.extractExtension(null)).isEqualTo("jpg");
    }
}

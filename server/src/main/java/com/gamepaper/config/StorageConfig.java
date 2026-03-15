package com.gamepaper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorageConfig implements WebMvcConfigurer {

    @Value("${storage.root:/app/storage}")
    private String storageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /storage/** 요청을 실제 파일 시스템 경로로 매핑
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("file:" + storageRoot + "/");
    }
}

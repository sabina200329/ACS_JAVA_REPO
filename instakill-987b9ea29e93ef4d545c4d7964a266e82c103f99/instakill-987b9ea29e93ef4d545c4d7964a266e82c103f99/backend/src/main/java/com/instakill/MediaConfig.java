package com.instakill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class MediaConfig implements WebMvcConfigurer {

    @Value("${media.base-path:media}")
    private String mediaBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path mediaDir = Paths.get(mediaBasePath);
        String absolutePath = mediaDir.toFile().getAbsolutePath();
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
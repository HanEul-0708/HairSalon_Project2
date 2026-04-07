package com.hairsalonproject2.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/images/**")
                .addResourceLocations("file:///" + uploadPath);

        registry.addResourceHandler("/files/download/**")
                .addResourceLocations("file:///" + uploadPath);

        registry.addResourceHandler("/uploads/review/**")
                .addResourceLocations("file:///" + Paths.get("uploads/review").toAbsolutePath().normalize() + "/");
    }
}

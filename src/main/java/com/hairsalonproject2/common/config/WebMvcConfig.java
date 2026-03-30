package com.hairsalonproject2.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WebMvcConfig
 *
 * 정적 리소스 경로를 추가로 매핑하는 설정 클래스
 *
 * 역할
 * 1. uploads 폴더에 저장된 파일을 브라우저에서 접근 가능하게 연결
 * 2. /uploads/** 요청이 들어오면 실제 로컬 uploads 폴더를 바라보도록 설정
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 정적 리소스 경로 등록
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 프로젝트 실행 위치 기준 uploads 폴더의 절대경로 생성
        Path uploadPath = Paths.get("uploads").toAbsolutePath();

        // /uploads/** 요청을 실제 uploads 폴더와 연결
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");
    }
}
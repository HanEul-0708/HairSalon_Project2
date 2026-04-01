package com.hairsalonproject2.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig — 정적 파일 경로 매핑 설정
 * =====================================================
 * 외부 폴더(C:/upload/)에 저장된 파일을
 * 브라우저가 URL로 접근할 수 있도록 연결해주는 설정
 *
 * 스프링 기본 정적 파일 경로는 static/ 폴더인데
 * 업로드 파일은 보안상 static/ 밖에 저장하기 때문에
 * 별도로 이 설정이 필요함
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * application.properties 의 file.upload.path 값을 읽어옴
     * 예) C:/upload/
     */
    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * URL 경로 ↔ 실제 폴더 경로 매핑
     *
     * /files/images/UUID.jpg 요청이 들어오면
     * → C:/upload/UUID.jpg 파일을 찾아서 응답
     *
     * /files/download/UUID.pdf 요청이 들어오면
     * → C:/upload/UUID.pdf 파일을 찾아서 응답
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 이미지 URL 매핑
        // 썸머노트가 "/files/images/UUID.jpg" 로 이미지를 불러올 때 사용
        registry.addResourceHandler("/files/images/**")
                .addResourceLocations("file:///" + uploadPath);

        // 첨부파일 URL 매핑
        // 다운로드 링크 클릭 시 FileController 가 직접 처리하므로
        // 여기선 이미지만 매핑해도 충분함
        registry.addResourceHandler("/files/download/**")
                .addResourceLocations("file:///" + uploadPath);
    }
}
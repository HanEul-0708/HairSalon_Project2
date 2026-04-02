package com.hairsalonproject2.common.file.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 파일 업로드 응답 DTO
 */
@Getter
@Builder
public class FileResponse {

    /** 파일 접근 URL */
    private String url;

    /** 원본 파일명 */
    private String originalFilename;
}

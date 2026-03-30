package com.HairSalonProject2.common.file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * FileResponse — 파일 업로드 결과 응답
 * =====================================================
 * 썸머노트 에디터에서 이미지 업로드 후
 * 서버가 "이 URL로 이미지 불러와" 라고 알려주는 응답 형식
 *
 * 썸머노트가 기대하는 JSON 형식:
 * { "url": "/files/images/a3f9b2c1.jpg" }
 */
@Getter
@AllArgsConstructor
public class FileResponse {

    /** 업로드된 파일에 접근할 수 있는 URL */
    private String url;

    /** 원래 파일명 (첨부파일 다운로드 시 표시용) */
    private String originalFilename;

    /** 저장된 파일명 (삭제 등 서버 작업에 사용) */
    private String storedFilename;
}
package com.HairSalonProject2.common.file.controller;

import com.HairSalonProject2.common.file.dto.FileResponse;
import com.HairSalonProject2.common.file.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FileController — 파일 업로드 / 다운로드 URL 처리
 * =====================================================
 * POST /files/images            → 썸머노트 이미지 업로드
 * GET  /files/download/{파일명} → 첨부파일 다운로드
 *
 * @RestController → 반환값을 JSON으로 자동 변환
 *                   (일반 @Controller와 달리 HTML을 반환하지 않음)
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * 파일 저장 경로
     * application.properties 의 file.upload.path 값을 읽어옴
     * 예) C:/upload/
     *
     * @Value 는 반드시 클래스 안에 있어야 함 (클래스 밖이면 오류!)
     */
    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 썸머노트 에디터 이미지 업로드
     * POST /files/images
     *
     * 썸머노트가 이미지 첨부 시 자동으로 이 URL로 POST 요청을 보냄
     * 저장 후 {"url": "/files/images/UUID.jpg"} 형태로 응답
     * 썸머노트가 이 url로 에디터 안에 이미지를 바로 표시해줌
     */
    @PostMapping("/images")
    public ResponseEntity<FileResponse> uploadEditorImage(
            @RequestParam("file") MultipartFile file) {

        try {
            FileResponse response = fileUploadService.uploadEditorImage(file);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            // 파일 저장 실패 (디스크 오류 등)
            return ResponseEntity.internalServerError().build();

        } catch (IllegalArgumentException e) {
            // 이미지가 아닌 파일 업로드 시도 등 잘못된 요청
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 첨부파일 다운로드
     * GET /files/download/{storedFilename}
     *
     * 브라우저가 이 URL 요청 시 파일을 직접 다운로드하게 함
     * Content-Disposition: attachment → 브라우저에 "다운로드로 처리해" 지시
     *
     * @param storedFilename   저장된 UUID 파일명 (URL 경로 변수)
     * @param originalFilename 다운로드 시 보여줄 원래 파일명 (쿼리 파라미터)
     */
    @GetMapping("/download/{storedFilename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String storedFilename,
            @RequestParam String originalFilename) {

        try {
            // 저장 경로 + UUID 파일명 합쳐서 실제 파일 위치 찾기
            Path filePath = Paths.get(uploadPath).resolve(storedFilename);
            Resource resource = new UrlResource(filePath.toUri());

            // 파일이 존재하지 않으면 404 반환
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // 한글 파일명 깨짐 방지 인코딩
            // URLEncoder의 + 를 %20(공백)으로 바꿔야 파일명이 정상 표시됨
            String encodedName = URLEncoder.encode(
                            originalFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedName)
                    .body(resource);

        } catch (MalformedURLException e) {
            // 잘못된 파일 경로 요청
            return ResponseEntity.badRequest().build();
        }
    }
}
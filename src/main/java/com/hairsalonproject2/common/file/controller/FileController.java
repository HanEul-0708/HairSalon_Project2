package com.hairsalonproject2.common.file.controller;

import com.hairsalonproject2.board.repository.BoardFileRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.file.dto.FileResponse;
import com.hairsalonproject2.common.file.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
 * FileController
 * 파일 업로드 / 다운로드 / 이미지 표시 담당
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;
    private final BoardFileRepository boardFileRepository;

    @Value("${file.upload.path}")
    private String uploadPath;

    @PostMapping("/images")
    public ResponseEntity<?> uploadEditorImage(@RequestParam("file") MultipartFile file) {
        try {
            FileResponse response = fileUploadService.uploadEditorImage(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("이미지 저장 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/images/{storedFilename}")
    public ResponseEntity<Resource> viewImage(@PathVariable String storedFilename) {
        try {
            if (isQnaAttachment(storedFilename)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource resource = loadSafeResource(resolveImageSubDirectory(storedFilename), storedFilename);
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = MediaTypeFactory.getMediaType(storedFilename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/download/{storedFilename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedFilename,
                                                 @RequestParam String originalFilename) {
        try {
            if (isQnaAttachment(storedFilename)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource resource = loadSafeResource("files", storedFilename);
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }

            String encodedName = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedName)
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean isQnaAttachment(String storedFilename) {
        return boardFileRepository.findBySavedNameWithBoard(storedFilename)
                .map(boardFile -> boardFile.getBoard().getType() == BoardType.QNA)
                .orElse(false);
    }

    private String resolveImageSubDirectory(String storedFilename) {
        return boardFileRepository.findBySavedNameWithBoard(storedFilename)
                .map(boardFile -> "files")
                .orElse("images");
    }

    private Resource loadSafeResource(String subDirectory, String storedFilename) throws MalformedURLException {
        Path basePath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(subDirectory).resolve(storedFilename).normalize();

        if (!filePath.startsWith(basePath)) {
            return null;
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) {
            return null;
        }

        return resource;
    }
}

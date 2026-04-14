package com.hairsalonproject2.common.file.service;

import com.hairsalonproject2.common.file.dto.FileResponse;
import com.hairsalonproject2.common.file.entity.UploadFile;
import com.hairsalonproject2.common.file.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * FileUploadService
 * 파일 업로드 처리 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final FileStore fileStore;

    /**
     * 파일 저장 경로
     */
    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 일반 첨부파일 최대 크기
     * 기본값: 10MB
     */
    @Value("${file.max-size:10485760}")
    private long maxFileSize;

    /**
     * 에디터 이미지 최대 크기
     * 기본값: 5MB
     */
    @Value("${file.image.max-size:5242880}")
    private long maxImageSize;

    /**
     * 일반 첨부파일 허용 확장자
     * 필요한 것만 열어 둔다.
     */
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "hwp", "hwpx", "txt", "zip", "jpg", "jpeg", "png"
    );

    /**
     * 에디터 이미지 허용 확장자
     */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    public UploadFile uploadFile(MultipartFile file) throws IOException {
        validateFile(file);
        return fileStore.storeAttachmentFile(file);
    }

    public FileResponse uploadEditorImage(MultipartFile file) throws IOException {
        validateEditorImage(file);

        UploadFile uploadFile;
        try {
            uploadFile = fileStore.storeImageFile(file);
        } catch (IOException e) {
            log.error("Failed to store editor image. uploadPath={}", uploadPath, e);
            throw e;
        }

        return FileResponse.builder()
                .url("/files/images/" + uploadFile.getStoredFilename())
                .originalFilename(uploadFile.getOriginalFilename())
                .build();
    }

    /**
     * 일반 첨부파일 검증
     */
    private void validateFile(MultipartFile file) {
        validateBasic(file);

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("첨부파일은 최대 10MB까지 업로드할 수 있습니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());

        if (!ALLOWED_FILE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 첨부파일 형식입니다.");
        }

        // 위험 확장자 추가 차단
        validateDangerousExtension(extension);
    }

    /**
     * 에디터 이미지 검증
     */
    private void validateEditorImage(MultipartFile file) {
        validateBasic(file);

        if (file.getSize() > maxImageSize) {
            throw new IllegalArgumentException("이미지는 최대 5MB까지 업로드할 수 있습니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("이미지 MIME 형식이 아닙니다.");
        }

        validateDangerousExtension(extension);
    }

    /**
     * 공통 기본 검증
     */
    private void validateBasic(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("파일 이름이 올바르지 않습니다.");
        }

        if (!originalFilename.contains(".")) {
            throw new IllegalArgumentException("확장자가 없는 파일은 업로드할 수 없습니다.");
        }
    }

    /**
     * 확장자 추출
     */
    private String extractExtension(String originalFilename) {
        return originalFilename
                .substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 위험 확장자 차단
     */
    private void validateDangerousExtension(String extension) {
        Set<String> blockedExtensions = Set.of(
                "exe", "sh", "bat", "cmd", "com", "msi",
                "jsp", "php", "asp", "aspx", "js", "html", "htm"
        );

        if (blockedExtensions.contains(extension)) {
            throw new IllegalArgumentException("위험한 파일 형식은 업로드할 수 없습니다.");
        }
    }
}

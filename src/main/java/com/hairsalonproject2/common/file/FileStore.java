package com.hairsalonproject2.common.file;

import com.hairsalonproject2.common.file.entity.UploadFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 파일 실제 저장/삭제 담당
 */
@Component
public class FileStore {

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 일반 첨부파일 저장
     */
    public UploadFile storeAttachmentFile(MultipartFile multipartFile) throws IOException {
        return storeFile(multipartFile, "files");
    }

    /**
     * 에디터 이미지 저장
     */
    public UploadFile storeImageFile(MultipartFile multipartFile) throws IOException {
        return storeFile(multipartFile, "images");
    }

    /**
     * 일반 첨부파일 삭제
     */
    public void deleteAttachmentFile(String storedFilename) {
        deleteFile("files", storedFilename);
    }

    /**
     * 이미지 삭제
     */
    public void deleteImageFile(String storedFilename) {
        deleteFile("images", storedFilename);
    }

    /**
     * 실제 파일 저장 공통 로직
     */
    private UploadFile storeFile(MultipartFile multipartFile, String subDirectory) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String storedFilename = createStoredFilename(originalFilename);

        Path targetDirectory = Path.of(uploadPath, subDirectory);
        Files.createDirectories(targetDirectory);

        Path targetPath = targetDirectory.resolve(storedFilename);
        multipartFile.transferTo(targetPath.toFile());

        return new UploadFile(originalFilename, storedFilename);
    }

    /**
     * 실제 파일 삭제 공통 로직
     */
    private void deleteFile(String subDirectory, String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }

        Path targetPath = Path.of(uploadPath, subDirectory, storedFilename);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // 삭제 실패는 서비스 흐름을 끊지 않도록 무시
        }
    }

    /**
     * UUID 기반 저장용 파일명 생성
     */
    private String createStoredFilename(String originalFilename) {
        String ext = extractExt(originalFilename);
        return UUID.randomUUID() + "." + ext;
    }

    /**
     * 확장자 추출
     */
    private String extractExt(String originalFilename) {
        int dotPos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(dotPos + 1);
    }
}
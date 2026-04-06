package com.hairsalonproject2.review.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * LocalFileStorageService
 *
 * 로컬 폴더에 업로드 파일을 저장하는 구현체
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    /**
     * 업로드 파일 저장 폴더
     */
    private final Path uploadDir = Paths.get("uploads/review");

    @Override
    public String storeFile(MultipartFile file) {

        try {
            // 업로드 폴더가 없으면 생성
            if (Files.notExists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 원본 파일명 정리
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

            // 파일명 중복 방지를 위해 UUID 사용
            String savedFileName = UUID.randomUUID() + "_" + originalFilename;

            // 실제 저장 경로
            Path targetPath = uploadDir.resolve(savedFileName);

            // 파일 저장
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // DB에는 접근 경로 문자열 저장
            return "/uploads/review/" + savedFileName;

        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장에 실패했습니다.");
        }
    }
}
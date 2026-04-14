package com.hairsalonproject2.review.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Override
    public String storeFile(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(uploadPath, "images").toAbsolutePath().normalize();
            if (Files.notExists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = "";
            int extensionIndex = originalFilename.lastIndexOf('.');
            if (extensionIndex >= 0) {
                extension = originalFilename.substring(extensionIndex);
            }

            String savedFileName = UUID.randomUUID() + extension;
            Path targetPath = uploadDir.resolve(savedFileName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/files/images/" + savedFileName;
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장에 실패했습니다.");
        }
    }
}

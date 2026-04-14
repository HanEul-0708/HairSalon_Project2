package com.hairsalonproject2.review.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageServiceTest {

    @TempDir
    Path uploadPath;

    @Test
    void storeFileSavesReviewImageUnderImagesDirectory() throws Exception {
        LocalFileStorageService storageService = new LocalFileStorageService();
        ReflectionTestUtils.setField(storageService, "uploadPath", uploadPath.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "review.png",
                "image/png",
                "review-image".getBytes()
        );

        String imageUrl = storageService.storeFile(file);

        assertThat(imageUrl).startsWith("/files/images/");
        String savedFileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        assertThat(Files.readString(uploadPath.resolve("images").resolve(savedFileName)))
                .isEqualTo("review-image");
    }
}

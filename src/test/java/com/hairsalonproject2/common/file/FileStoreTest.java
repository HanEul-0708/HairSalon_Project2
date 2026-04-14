package com.hairsalonproject2.common.file;

import com.hairsalonproject2.common.file.entity.UploadFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStoreTest {

    @TempDir
    Path uploadPath;

    @Test
    void storeImageFileSavesUnderImagesDirectory() throws Exception {
        FileStore fileStore = new FileStore();
        ReflectionTestUtils.setField(fileStore, "uploadPath", uploadPath.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "board.png",
                "image/png",
                "board-image".getBytes()
        );

        UploadFile uploadFile = fileStore.storeImageFile(file);

        assertThat(Files.readString(uploadPath.resolve("images").resolve(uploadFile.getStoredFilename())))
                .isEqualTo("board-image");
    }

    @Test
    void storeAttachmentFileSavesUnderFilesDirectory() throws Exception {
        FileStore fileStore = new FileStore();
        ReflectionTestUtils.setField(fileStore, "uploadPath", uploadPath.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "attach.txt",
                "text/plain",
                "attachment".getBytes()
        );

        UploadFile uploadFile = fileStore.storeAttachmentFile(file);

        assertThat(Files.readString(uploadPath.resolve("files").resolve(uploadFile.getStoredFilename())))
                .isEqualTo("attachment");
    }
}

package com.hairsalonproject2.common.file.controller;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.repository.BoardFileRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.file.service.FileUploadService;
import com.hairsalonproject2.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerSecurityTest {

    @InjectMocks
    private FileController fileController;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private BoardFileRepository boardFileRepository;

    @TempDir
    Path uploadPath;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileController, "uploadPath", uploadPath.toString());
    }

    @Test
    void qnaAttachmentImageRequestIsForbidden() {
        when(boardFileRepository.findBySavedNameWithBoard("qna-image.png"))
                .thenReturn(Optional.of(qnaAttachment("qna-image.png")));

        ResponseEntity<Resource> response = fileController.viewImage("qna-image.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void qnaAttachmentDownloadRequestIsForbidden() {
        when(boardFileRepository.findBySavedNameWithBoard("qna-file.pdf"))
                .thenReturn(Optional.of(qnaAttachment("qna-file.pdf")));

        ResponseEntity<Resource> response = fileController.downloadFile("qna-file.pdf", "qna-file.pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void editorImageRequestLoadsFromImagesDirectory() throws Exception {
        Files.createDirectories(uploadPath.resolve("images"));
        Files.writeString(uploadPath.resolve("images").resolve("editor-image.png"), "image-content");
        when(boardFileRepository.findBySavedNameWithBoard("editor-image.png"))
                .thenReturn(Optional.empty());

        ResponseEntity<Resource> response = fileController.viewImage("editor-image.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readBody(response)).isEqualTo("image-content");
    }

    @Test
    void noticeAttachmentImageRequestLoadsFromFilesDirectory() throws Exception {
        Files.createDirectories(uploadPath.resolve("files"));
        Files.writeString(uploadPath.resolve("files").resolve("notice-image.png"), "image-content");
        when(boardFileRepository.findBySavedNameWithBoard("notice-image.png"))
                .thenReturn(Optional.of(noticeAttachment("notice-image.png")));

        ResponseEntity<Resource> response = fileController.viewImage("notice-image.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readBody(response)).isEqualTo("image-content");
    }

    @Test
    void noticeAttachmentDownloadLoadsFromFilesDirectory() throws Exception {
        Files.createDirectories(uploadPath.resolve("files"));
        Files.writeString(uploadPath.resolve("files").resolve("notice-file.txt"), "file-content");
        when(boardFileRepository.findBySavedNameWithBoard("notice-file.txt"))
                .thenReturn(Optional.of(noticeAttachment("notice-file.txt")));

        ResponseEntity<Resource> response = fileController.downloadFile("notice-file.txt", "notice-file.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readBody(response)).isEqualTo("file-content");
    }

    private String readBody(ResponseEntity<Resource> response) throws Exception {
        assertThat(response.getBody()).isNotNull();
        try (InputStream inputStream = response.getBody().getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private BoardFile qnaAttachment(String savedName) {
        return attachment(savedName, BoardType.QNA);
    }

    private BoardFile noticeAttachment(String savedName) {
        return attachment(savedName, BoardType.NOTICE);
    }

    private BoardFile attachment(String savedName, BoardType boardType) {
        Member member = Member.builder()
                .memberId("user01")
                .password("encoded-password")
                .name("Tester")
                .phone("010-1234-5678")
                .email("tester@example.com")
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();

        Board board = Board.builder()
                .member(member)
                .type(boardType)
                .title(boardType.name())
                .content("content")
                .viewCount(0)
                .build();

        return BoardFile.builder()
                .board(board)
                .originalName(savedName)
                .savedName(savedName)
                .filePath(uploadPath.resolve("files").toString())
                .fileSize(1L)
                .fileExtension("png")
                .build();
    }
}

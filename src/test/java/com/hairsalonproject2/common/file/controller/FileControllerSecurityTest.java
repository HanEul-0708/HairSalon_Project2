package com.hairsalonproject2.common.file.controller;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.repository.BoardFileRepository;
import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.file.service.FileUploadService;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "file.upload.path=C:/upload/")
class FileControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileUploadService fileUploadService;

    @MockitoBean
    private BoardFileRepository boardFileRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void qnaAttachmentImageRequestIsForbidden() throws Exception {
        when(boardFileRepository.findBySavedNameWithBoard("qna-image.png"))
                .thenReturn(Optional.of(qnaAttachment("qna-image.png")));

        mockMvc.perform(get("/files/images/qna-image.png"))
                .andExpect(status().isForbidden());
    }

    @Test
    void qnaAttachmentDownloadRequestIsForbidden() throws Exception {
        when(boardFileRepository.findBySavedNameWithBoard("qna-file.pdf"))
                .thenReturn(Optional.of(qnaAttachment("qna-file.pdf")));

        mockMvc.perform(get("/files/download/qna-file.pdf").param("originalFilename", "qna-file.pdf"))
                .andExpect(status().isForbidden());
    }

    private BoardFile qnaAttachment(String savedName) {
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
                .type(BoardType.QNA)
                .title("QnA")
                .content("content")
                .viewCount(0)
                .build();

        return BoardFile.builder()
                .board(board)
                .originalName(savedName)
                .savedName(savedName)
                .filePath("C:/upload/" + savedName)
                .fileSize(1L)
                .fileExtension("png")
                .build();
    }
}

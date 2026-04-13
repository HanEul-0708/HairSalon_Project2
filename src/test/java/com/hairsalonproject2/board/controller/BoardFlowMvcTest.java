package com.hairsalonproject2.board.controller;

import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.board.service.BoardViewGuard;
import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BoardController.class)
@Import(SecurityConfig.class)
class BoardFlowMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private BoardViewGuard boardViewGuard;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void qnaCreateReplyAndDeleteFlow() throws Exception {
        when(boardService.create(any(), eq("user01"), any(), eq(BoardType.QNA))).thenReturn(10);
        when(boardService.getBoardType(10)).thenReturn(BoardType.QNA);

        mockMvc.perform(post("/boards/qna")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .with(csrf())
                        .param("title", "QnA title")
                        .param("content", "QnA content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/qna/10"));

        mockMvc.perform(post("/boards/qna/10/reply")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf())
                        .param("parentId", "10")
                        .param("title", "answer")
                        .param("content", "answer content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/qna/10"));

        mockMvc.perform(delete("/boards/10")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/qna"));

        InOrder inOrder = inOrder(boardService);
        inOrder.verify(boardService).create(any(), eq("user01"), any(), eq(BoardType.QNA));
        inOrder.verify(boardService).createReply(any(), eq("admin01"));
        inOrder.verify(boardService).getBoardType(10);
        inOrder.verify(boardService).delete(10, "admin01", true);
    }

    @Test
    void noticeCreateEditAndOtherAdminDeleteFlow() throws Exception {
        when(boardService.create(any(), eq("admin01"), any(), eq(BoardType.NOTICE))).thenReturn(21);
        when(boardService.getBoardType(21)).thenReturn(BoardType.NOTICE);
        when(boardService.getDetailOnly(21)).thenReturn(new BoardDetailResponse(
                21,
                BoardType.NOTICE,
                "Notice title",
                "Notice content",
                "admin01",
                0,
                null,
                null,
                null,
                false,
                null,
                0
        ));

        mockMvc.perform(post("/boards/notices")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf())
                        .param("title", "Notice title")
                        .param("content", "Notice content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/notices/21"));

        mockMvc.perform(get("/boards/21/edit")
                        .with(authenticationFor("admin01", MemberRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(view().name("board/edit"));

        mockMvc.perform(post("/boards/21/edit")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf())
                        .param("title", "Edited notice")
                        .param("content", "Edited content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/notices/21"));

        mockMvc.perform(get("/boards/21/edit")
                        .with(authenticationFor("admin02", MemberRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(view().name("board/edit"));

        mockMvc.perform(post("/boards/21/edit")
                        .with(authenticationFor("admin02", MemberRole.ADMIN))
                        .with(csrf())
                        .param("title", "Second admin edit")
                        .param("content", "Second admin content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/notices/21"));

        mockMvc.perform(delete("/boards/21")
                        .with(authenticationFor("admin02", MemberRole.ADMIN))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/notices"));

        InOrder inOrder = inOrder(boardService);
        inOrder.verify(boardService).create(any(), eq("admin01"), any(), eq(BoardType.NOTICE));
        inOrder.verify(boardService).getBoardType(21);
        inOrder.verify(boardService).getDetailOnly(21);
        inOrder.verify(boardService).getBoardType(21);
        inOrder.verify(boardService).update(eq(21), any(), eq("admin01"), any(), eq(true));
        inOrder.verify(boardService).getBoardType(21);
        inOrder.verify(boardService).getDetailOnly(21);
        inOrder.verify(boardService).getBoardType(21);
        inOrder.verify(boardService).update(eq(21), any(), eq("admin02"), any(), eq(true));
        inOrder.verify(boardService).getBoardType(21);
        inOrder.verify(boardService).delete(21, "admin02", true);
    }

    @Test
    void normalUserCannotEditNotice() throws Exception {
        when(boardService.getBoardType(21)).thenReturn(BoardType.NOTICE);
        when(boardService.isMyBoard(21, "user01")).thenReturn(false);

        mockMvc.perform(get("/boards/21/edit")
                        .with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/notices/21?error=forbidden"));

        verify(boardService).getBoardType(21);
        verify(boardService).isMyBoard(21, "user01");
    }

    @Test
    void anonymousUserCannotOpenQnaDetail() throws Exception {
        mockMvc.perform(get("/boards/qna/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/login"));
    }

    @Test
    void otherUserCannotOpenQnaDetail() throws Exception {
        when(boardService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));

        mockMvc.perform(get("/boards/qna/10")
                        .with(authenticationFor("user02", MemberRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/access-denied"));
    }

    @Test
    void designerCanOpenQnaDetail() throws Exception {
        when(boardService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));
        when(boardViewGuard.shouldIncreaseView(eq(10), eq(BoardType.QNA), any(), any())).thenReturn(false);

        mockMvc.perform(get("/boards/qna/10")
                        .with(authenticationFor("designer01", MemberRole.DESIGNER)))
                .andExpect(status().isOk())
                .andExpect(view().name("board/qna-detail"));
    }

    @Test
    void qnaDetailIncreasesViewAfterAccessCheck() throws Exception {
        when(boardService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));
        when(boardViewGuard.shouldIncreaseView(eq(10), eq(BoardType.QNA), any(), any())).thenReturn(true);
        when(boardService.getPublicDetailAndIncreaseView(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));

        mockMvc.perform(get("/boards/qna/10")
                        .with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isOk())
                .andExpect(view().name("board/qna-detail"));

        verify(boardService).getPublicDetail(10, BoardType.QNA);
        verify(boardService).getPublicDetailAndIncreaseView(10, BoardType.QNA);
    }

    @Test
    void otherUserCannotReportQna() throws Exception {
        when(boardService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));

        mockMvc.perform(post("/boards/qna/10/report")
                        .with(authenticationFor("user02", MemberRole.USER))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/access-denied"));

        verify(boardService).getPublicDetail(10, BoardType.QNA);
        verify(boardService, never()).reportBoard(eq(10), any());
    }

    @Test
    void designerCanReportQna() throws Exception {
        when(boardService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));
        when(boardService.reportBoard(10, "designer01")).thenReturn(true);

        mockMvc.perform(post("/boards/qna/10/report")
                        .with(authenticationFor("designer01", MemberRole.DESIGNER))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/boards/qna/10?reported=true"));

        verify(boardService).getPublicDetail(10, BoardType.QNA);
        verify(boardService).reportBoard(10, "designer01");
    }

    private BoardDetailResponse qnaDetail(String memberId) {
        return new BoardDetailResponse(
                10,
                BoardType.QNA,
                "QnA title",
                "QnA content",
                memberId,
                0,
                null,
                LocalDateTime.of(2026, 4, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 10, 0),
                false,
                null,
                0
        );
    }

    private RequestPostProcessor authenticationFor(String memberId, MemberRole role) {
        Member member = Member.builder()
                .memberId(memberId)
                .password("encoded-password")
                .name("Tester")
                .phone("010-1234-5678")
                .email(memberId + "@example.com")
                .role(role)
                .status(MemberStatus.ACTIVE)
                .build();

        CustomUserDetails principal = new CustomUserDetails(member);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}

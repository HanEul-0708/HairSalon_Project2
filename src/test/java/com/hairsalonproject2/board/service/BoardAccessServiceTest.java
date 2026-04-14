package com.hairsalonproject2.board.service;

import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.common.constant.BoardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardAccessServiceTest {

    @Mock
    private BoardQueryService boardQueryService;

    @Mock
    private BoardAdminService boardAdminService;

    @InjectMocks
    private BoardAccessService boardAccessService;

    @Test
    void getQnaPageForViewerMasksOtherUsersTitle() {
        when(boardQueryService.getQnaPage(0, 10, null)).thenReturn(new PageImpl<>(List.of(
                boardResponse(10, "private question", "user01")
        )));

        BoardResponse board = boardAccessService.getQnaPageForViewer(0, 10, null, "user02", false)
                .getContent()
                .get(0);

        assertThat(board.getTitle()).isEqualTo("비밀글입니다.");
        assertThat(board.isAccessible()).isFalse();
    }

    @Test
    void getQnaPageForViewerKeepsTitleForOwner() {
        when(boardQueryService.getQnaPage(0, 10, null)).thenReturn(new PageImpl<>(List.of(
                boardResponse(10, "my question", "user01")
        )));

        BoardResponse board = boardAccessService.getQnaPageForViewer(0, 10, null, "user01", false)
                .getContent()
                .get(0);

        assertThat(board.getTitle()).isEqualTo("my question");
        assertThat(board.isAccessible()).isTrue();
    }

    @Test
    void getAccessibleQnaDetailRejectsOtherUsers() {
        when(boardQueryService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));

        assertThatThrownBy(() -> boardAccessService.getAccessibleQnaDetail(10, "user02", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAccessibleQnaDetailAndIncreaseViewDoesNotIncreaseWhenDenied() {
        when(boardQueryService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));

        assertThatThrownBy(() -> boardAccessService.getAccessibleQnaDetailAndIncreaseView(10, "user02", false))
                .isInstanceOf(AccessDeniedException.class);

        verify(boardQueryService, never()).getPublicDetailAndIncreaseView(10, BoardType.QNA);
    }

    @Test
    void reportAccessibleQnaChecksAccessBeforeReporting() {
        when(boardQueryService.getPublicDetail(10, BoardType.QNA)).thenReturn(qnaDetail("user01"));
        when(boardAdminService.reportBoard(10, "designer01")).thenReturn(true);

        boolean reported = boardAccessService.reportAccessibleQna(10, "designer01", true);

        assertThat(reported).isTrue();
        verify(boardAdminService).reportBoard(10, "designer01");
    }

    @Test
    void canEditBoardAllowsAdminNoticeEdit() {
        when(boardQueryService.getBoardType(10)).thenReturn(BoardType.NOTICE);

        assertThat(boardAccessService.canEditBoard(10, "admin01", true)).isTrue();

        verify(boardQueryService, never()).isMyBoard(10, "admin01");
    }

    @Test
    void canEditBoardUsesOwnershipForNonNoticeAdminEdit() {
        when(boardQueryService.getBoardType(10)).thenReturn(BoardType.QNA);
        when(boardQueryService.isMyBoard(10, "user01")).thenReturn(true);

        assertThat(boardAccessService.canEditBoard(10, "user01", false)).isTrue();
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
                LocalDateTime.of(2026, 4, 14, 9, 0),
                LocalDateTime.of(2026, 4, 14, 9, 0),
                false,
                null,
                0
        );
    }

    private BoardResponse boardResponse(Integer boardId, String title, String memberId) {
        return new BoardResponse(
                boardId,
                BoardType.QNA,
                title,
                memberId,
                0,
                LocalDateTime.of(2026, 4, 14, 9, 0),
                false,
                0L,
                false,
                0,
                false
        );
    }
}

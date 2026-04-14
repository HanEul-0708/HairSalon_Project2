package com.hairsalonproject2.board.service;

import com.hairsalonproject2.admin.entity.BoardDeleteLog;
import com.hairsalonproject2.admin.repository.BoardDeleteLogRepository;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.repository.BoardReportRepository;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardAdminServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardReportRepository boardReportRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BoardDeleteLogRepository boardDeleteLogRepository;

    @Mock
    private BoardAttachmentService boardAttachmentService;

    @InjectMocks
    private BoardAdminService boardAdminService;

    @Test
    void adminDeleteSavesLogAndRemovesChildrenBeforeDeletingBoard() {
        Board board = qnaBoard(10, "user01");
        when(boardRepository.findById(10)).thenReturn(Optional.of(board));

        boardAdminService.adminDelete(10, "admin01", "cleanup");

        InOrder inOrder = inOrder(
                boardDeleteLogRepository,
                boardAttachmentService,
                boardReportRepository,
                boardRepository
        );
        inOrder.verify(boardDeleteLogRepository).save(any(BoardDeleteLog.class));
        inOrder.verify(boardAttachmentService).deletePhysicalFiles(board);
        inOrder.verify(boardReportRepository).deleteByBoardBoardId(10);
        inOrder.verify(boardRepository).deleteImagesByBoardId(10);
        inOrder.verify(boardRepository).deleteFilesByBoardId(10);
        inOrder.verify(boardRepository).deleteBoardRowById(10);
    }

    private Board qnaBoard(Integer boardId, String memberId) {
        Member member = Member.builder()
                .memberId(memberId)
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
        ReflectionTestUtils.setField(board, "boardId", boardId);
        return board;
    }
}

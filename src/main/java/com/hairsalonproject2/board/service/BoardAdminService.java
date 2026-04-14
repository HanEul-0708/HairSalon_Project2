package com.hairsalonproject2.board.service;

import com.hairsalonproject2.admin.entity.BoardDeleteLog;
import com.hairsalonproject2.admin.repository.BoardDeleteLogRepository;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardReport;
import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.board.repository.BoardReportRepository;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BoardAdminService
 *
 * 게시판 관리자/신고 처리 전용 서비스
 *
 * 담당 역할
 * 1. 게시글 신고
 * 2. 숨김 / 숨김 해제
 * 3. 신고 수 초기화
 * 4. 강제 삭제
 * 5. 삭제 로그 저장
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardAdminService {

    private final BoardRepository boardRepository;
    private final BoardReportRepository boardReportRepository;
    private final MemberRepository memberRepository;
    private final BoardDeleteLogRepository boardDeleteLogRepository;
    private final BoardAttachmentService boardAttachmentService;

    /**
     * 게시글 신고
     *
     * @return true  = 이번에 새로 신고됨
     *         false = 이미 신고한 이력이 있음
     */
    public boolean reportBoard(Integer boardId, String reporterId) {
        Board board = findBoard(boardId);
        Member reporter = findMember(reporterId);

        if (isWriter(board, reporterId)) {
            throw new BoardException("본인 글은 신고할 수 없습니다.");
        }

        boolean alreadyReported = boardReportRepository.existsByBoardBoardIdAndMemberMemberId(boardId, reporterId);
        if (alreadyReported) {
            return false;
        }

        BoardReport boardReport = BoardReport.builder()
                .board(board)
                .member(reporter)
                .build();

        boardReportRepository.save(boardReport);
        board.increaseReportCount();
        return true;
    }

    public void hideBoard(Integer boardId, String reason) {
        Board board = findBoard(boardId);
        board.hide(normalizeReason(reason, "관리자 숨김 처리"));
    }

    public void unhideBoard(Integer boardId) {
        Board board = findBoard(boardId);
        board.unhide();
    }

    /**
     * 신고 수 초기화
     * - 게시글 신고 이력도 함께 삭제
     */
    public void resetReportCount(Integer boardId) {
        Board board = findBoard(boardId);

        boardReportRepository.deleteByBoardBoardId(boardId);
        board.resetReportCount();
    }

    /**
     * 관리자 강제 삭제
     */
    public void adminDelete(Integer boardId, String adminId, String reason) {
        Board board = findBoard(boardId);

        saveDeleteLog(board, adminId, normalizeReason(reason, "관리자 강제 삭제"));
        deleteBoardRecursively(board);
    }

    private Board findBoard(Integer boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardException("존재하지 않는 게시글입니다."));
    }

    private Member findMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BoardException("존재하지 않는 회원입니다."));
    }

    private boolean isWriter(Board board, String memberId) {
        return board.getMember() != null
                && Objects.equals(board.getMember().getMemberId(), memberId);
    }

    private String normalizeReason(String reason, String defaultValue) {
        if (reason == null || reason.trim().isEmpty()) {
            return defaultValue;
        }
        return reason.trim();
    }

    private void saveDeleteLog(Board board, String adminId, String reason) {
        BoardDeleteLog log = BoardDeleteLog.builder()
                .boardId(board.getBoardId())
                .boardType(board.getType())
                .boardTitle(board.getTitle())
                .writerId(board.getMember().getMemberId())
                .deletedBy(adminId)
                .deleteReason(reason)
                .build();

        boardDeleteLogRepository.save(log);
    }

    private void deleteBoardRecursively(Board board) {
        List<Board> children = new ArrayList<>(board.getChildren());

        for (Board child : children) {
            deleteBoardRecursively(child);
        }

        boardAttachmentService.deletePhysicalFiles(board);

        if (board.getParent() != null) {
            board.changeParent(null);
        }

        boardReportRepository.deleteByBoardBoardId(board.getBoardId());
        boardRepository.delete(board);
    }
}

package com.hairsalonproject2.board.service;

import com.hairsalonproject2.admin.dto.response.BoardDeleteLogResponse;
import com.hairsalonproject2.admin.repository.BoardDeleteLogRepository;
import com.hairsalonproject2.board.dto.request.BoardSearchRequest;
import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardReplyResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.dto.response.BoardSummaryResponse;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardQueryService {

    private static final String BOARD_NOT_FOUND_MESSAGE = "존재하지 않는 게시글입니다.";

    private final BoardRepository boardRepository;
    private final BoardDeleteLogRepository boardDeleteLogRepository;

    public Page<BoardResponse> getNoticePage(int page, int size, String keyword) {
        return getBoardPage(BoardType.NOTICE, keyword, page, size);
    }

    public Page<BoardResponse> getQnaPage(int page, int size, String keyword) {
        return getBoardPage(BoardType.QNA, keyword, page, size);
    }

    public Page<BoardResponse> getQnaPage(int page,
                                          int size,
                                          String keyword,
                                          String viewerMemberId,
                                          boolean canViewAll) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchAccessiblePageByTypeAndKeyword(
                BoardType.QNA,
                normalizeKeyword(keyword),
                normalizeKeyword(viewerMemberId),
                canViewAll,
                pageable
        );
    }

    public Page<BoardResponse> getBoardPage(BoardType type, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchPageByTypeAndKeyword(type, normalizeKeyword(keyword), pageable);
    }

    public Page<BoardResponse> search(BoardSearchRequest request, int page, int size) {
        return getBoardPage(request.getType(), request.getKeyword(), page, size);
    }

    public List<BoardSummaryResponse> getRecentNotices() {
        return boardRepository.findByTypeAndParentIsNullOrderByBoardIdDesc(BoardType.NOTICE)
                .stream()
                .filter(board -> !board.isHidden())
                .limit(5)
                .map(BoardSummaryResponse::from)
                .collect(Collectors.toList());
    }

    public Page<BoardResponse> getAdminBoardPage(BoardType type, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchAdminPage(type, normalizeKeyword(keyword), pageable);
    }

    public long countOriginalBoards(BoardType type) {
        return boardRepository.countByTypeAndParentIsNullAndHiddenFalse(type);
    }

    public long countTodayBoards() {
        return boardRepository.countByParentIsNullAndCreatedAtGreaterThanEqual(
                LocalDateTime.now().toLocalDate().atStartOfDay()
        );
    }

    public long countHiddenBoards() {
        return boardRepository.countByParentIsNullAndHiddenTrue();
    }

    public long countReportedBoards() {
        return boardRepository.countByParentIsNullAndReportCountGreaterThan(0);
    }

    public List<BoardDeleteLogResponse> getRecentDeleteLogs() {
        return boardDeleteLogRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(BoardDeleteLogResponse::from)
                .collect(Collectors.toList());
    }

    public BoardDetailResponse getDetailOnly(Integer boardId) {
        return buildDetailResponse(findBoardDetail(boardId));
    }

    public BoardDetailResponse getPublicDetail(Integer boardId, BoardType type) {
        Board board = findBoardDetail(boardId);
        validatePublicBoard(board, type);
        return buildDetailResponse(board);
    }

    @Transactional
    public BoardDetailResponse getDetailAndIncreaseView(Integer boardId) {
        Board board = findBoardDetail(boardId);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    @Transactional
    public BoardDetailResponse getPublicDetailAndIncreaseView(Integer boardId, BoardType type) {
        Board board = findBoardDetail(boardId);
        validatePublicBoard(board, type);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    public BoardType getBoardType(Integer boardId) {
        return findBoard(boardId).getType();
    }

    public List<BoardResponse> getMyBoards(String memberId) {
        return boardRepository.findByMemberMemberIdOrderByBoardIdDesc(memberId)
                .stream()
                .map(this::toBoardResponse)
                .collect(Collectors.toList());
    }

    public boolean isMyBoard(Integer boardId, String memberId) {
        return boardRepository.findById(boardId)
                .map(board -> board.getMember().getMemberId().equals(memberId))
                .orElse(false);
    }

    private Board findBoard(Integer boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardException(BOARD_NOT_FOUND_MESSAGE));
    }

    private Board findBoardDetail(Integer boardId) {
        return boardRepository.findDetailByBoardId(boardId)
                .orElseThrow(() -> new BoardException(BOARD_NOT_FOUND_MESSAGE));
    }

    private BoardDetailResponse buildDetailResponse(Board board) {
        BoardDetailResponse response = BoardDetailResponse.from(board);
        response.setImages(boardRepository.findImagesByBoardId(board.getBoardId())
                .stream()
                .map(BoardDetailResponse.ImageInfo::from)
                .collect(Collectors.toList()));
        response.setFiles(boardRepository.findFilesByBoardId(board.getBoardId())
                .stream()
                .map(BoardDetailResponse.FileInfo::from)
                .collect(Collectors.toList()));
        List<BoardReplyResponse> replies = boardRepository.findReplyResponsesByParentBoardId(board.getBoardId());
        response.setReplies(replies);
        response.setAnswered(!replies.isEmpty());
        return response;
    }

    private void validatePublicBoard(Board board, BoardType type) {
        if (board.getType() != type) {
            throw new BoardException("게시판 종류가 올바르지 않습니다.");
        }
        if (board.isHidden()) {
            throw new BoardException("숨김 처리된 게시글입니다.");
        }
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 50);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BoardResponse toBoardResponse(Board board) {
        long replyCount = board.getChildren() == null ? 0L : board.getChildren().size();

        return new BoardResponse(
                board.getBoardId(),
                board.getType(),
                board.getTitle(),
                board.getMember().getMemberId(),
                board.getViewCount(),
                board.getCreatedAt(),
                false,
                replyCount,
                board.isHidden(),
                board.getReportCount(),
                replyCount > 0
        );
    }
}

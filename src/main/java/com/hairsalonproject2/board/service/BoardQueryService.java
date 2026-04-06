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

/**
 * BoardQueryService
 *
 * 게시판 조회 전용 서비스
 *
 * 담당 역할
 * 1. 공지/문의 목록 조회
 * 2. 상세 조회
 * 3. 내 글 조회
 * 4. 관리자 목록 조회
 * 5. 통계 조회
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardQueryService {

    private final BoardRepository boardRepository;
    private final BoardDeleteLogRepository boardDeleteLogRepository;

    public Page<BoardResponse> getNoticePage(int page, int size, String keyword) {
        return getBoardPage(BoardType.NOTICE, keyword, page, size);
    }

    public Page<BoardResponse> getQnaPage(int page, int size, String keyword) {
        return getBoardPage(BoardType.QNA, keyword, page, size);
    }

    public Page<BoardResponse> getBoardPage(BoardType type, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchPageByTypeAndKeyword(type, normalizeKeyword(keyword), pageable);
    }

    public Page<BoardResponse> search(BoardSearchRequest request, int page, int size) {
        return getBoardPage(request.getType(), request.getKeyword(), page, size);
    }

    /**
     * 최근 공지 5개 조회
     *
     * 현재 Repository 에 없는
     * findTop5ByTypeAndParentIsNullAndHiddenFalseOrderByBoardIdDesc() 대신
     * 기존 조회 메서드 결과를 서비스에서 5개로 제한한다.
     */
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
        return buildDetailResponse(findBoard(boardId));
    }

    public BoardDetailResponse getPublicDetail(Integer boardId, BoardType type) {
        Board board = findBoard(boardId);
        validatePublicBoard(board, type);
        return buildDetailResponse(board);
    }

    /**
     * 상세 조회 + 조회수 증가
     *
     * 조회성 서비스지만 기존 공개 API 유지 위해 포함
     */
    @Transactional
    public BoardDetailResponse getDetailAndIncreaseView(Integer boardId) {
        Board board = findBoard(boardId);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    @Transactional
    public BoardDetailResponse getPublicDetailAndIncreaseView(Integer boardId, BoardType type) {
        Board board = findBoard(boardId);
        validatePublicBoard(board, type);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    @Transactional
    public void increaseViewCount(Integer boardId) {
        boardRepository.increaseViewCount(boardId);
    }

    public BoardType getBoardType(Integer boardId) {
        return findBoard(boardId).getType();
    }

    /**
     * 내가 작성한 글 목록 조회
     *
     * 현재 Repository 에 없는 findMyBoardResponses() 대신
     * 존재하는 엔티티 조회 메서드로 가져온 뒤 DTO 로 변환한다.
     */
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
                .orElseThrow(() -> new BoardException("존재하지 않는 게시글입니다."));
    }

    /**
     * 상세 응답 생성
     *
     * 현재 Repository 에 없는 findReplyResponsesByParentBoardId() 대신
     * 존재하는 findByParentBoardIdOrderByBoardIdAsc() 결과를 DTO 로 변환한다.
     */
    private BoardDetailResponse buildDetailResponse(Board board) {
        BoardDetailResponse response = BoardDetailResponse.from(board);

        List<BoardReplyResponse> replies = boardRepository.findByParent_BoardIdOrderByBoardIdAsc(board.getBoardId())
                .stream()
                .filter(reply -> !reply.isHidden())
                .map(BoardReplyResponse::from)
                .collect(Collectors.toList());

        response.setReplies(replies);
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

    /**
     * Board 엔티티 -> BoardResponse DTO 변환
     *
     * 현재 BoardRepository JPQL 생성자 순서와 맞춘다.
     */
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
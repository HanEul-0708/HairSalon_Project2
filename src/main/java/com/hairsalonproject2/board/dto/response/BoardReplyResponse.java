package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.util.HtmlSanitizer;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * BoardReplyResponse
 * 답글 목록 표시 전용 DTO
 */
@Getter
public class BoardReplyResponse {

    /**
     * 답글 번호
     */
    private Integer boardId;

    /**
     * 부모 글 번호
     */
    private Integer parentBoardId;

    /**
     * 게시판 종류
     */
    private String type;

    /**
     * 답글 제목
     */
    private String title;

    /**
     * 답글 본문
     */
    private String content;

    /**
     * 작성자 회원 ID
     */
    private String memberId;

    /**
     * 작성일시
     */
    private LocalDateTime createdAt;

    /**
     * 수정일시
     */
    private LocalDateTime updatedAt;

    public BoardReplyResponse() {
    }

    /**
     * JPQL DTO 조회용 생성자
     */
    public BoardReplyResponse(Integer boardId,
                              Integer parentBoardId,
                              BoardType type,
                              String title,
                              String content,
                              String memberId,
                              LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
        this.boardId = boardId;
        this.parentBoardId = parentBoardId;
        this.type = type != null ? type.name() : null;
        this.title = title;
        this.content = HtmlSanitizer.sanitize(content);
        this.memberId = memberId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 정적 생성 메서드
     */
    public static BoardReplyResponse from(Board board) {
        BoardReplyResponse response = new BoardReplyResponse();

        response.boardId = board.getBoardId();
        response.parentBoardId = (board.getParent() != null)
                ? board.getParent().getBoardId()
                : null;
        response.type = board.getType().name();
        response.title = board.getTitle();
        response.content = HtmlSanitizer.sanitize(board.getContent());
        response.memberId = board.getMember().getMemberId();
        response.createdAt = board.getCreatedAt();
        response.updatedAt = board.getUpdatedAt();

        return response;
    }
}

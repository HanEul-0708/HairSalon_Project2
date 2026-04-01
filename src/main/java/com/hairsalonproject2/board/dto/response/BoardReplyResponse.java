package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * BoardReplyResponse
 * 답글 목록 표시 전용 DTO
 *
 * 이유:
 * 기존 BoardResponse 는 목록 화면용이라
 * 답글 본문(content)이 없다.
 * 그래서 상세 화면의 답글 영역에는 전용 DTO를 쓰는 것이 안전하다.
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
        response.content = board.getContent();
        response.memberId = board.getMember().getMemberId();
        response.createdAt = board.getCreatedAt();
        response.updatedAt = board.getUpdatedAt();

        return response;
    }
}
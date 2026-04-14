package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * BoardSummaryResponse
 * 간단 요약용 DTO
 */
@Getter
public class BoardSummaryResponse {

    /**
     * 게시글 번호
     */
    private Integer boardId;

    /**
     * 게시판 타입
     */
    private String type;

    /**
     * 제목
     */
    private String title;

    /**
     * 작성일시
     */
    private LocalDateTime createdAt;

    /**
     * 조회수
     */
    private Integer viewCount;

    public static BoardSummaryResponse from(Board board) {
        BoardSummaryResponse response = new BoardSummaryResponse();
        response.boardId = board.getBoardId();
        response.type = board.getType().name();
        response.title = board.getTitle();
        response.createdAt = board.getCreatedAt();
        response.viewCount = board.getViewCount();
        return response;
    }
}
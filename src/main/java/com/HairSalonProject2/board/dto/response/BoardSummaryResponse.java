package com.HairSalonProject2.board.dto.response;

import com.HairSalonProject2.board.entity.Board;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BoardSummaryResponse — 게시글 간단 요약 응답 DTO
 * =====================================================
 * 메인 홈이나 사이드바 등 공간이 제한된 곳에서
 * 게시글을 간단하게 보여줄 때 사용
 *
 * BoardResponse 와 차이점
 * - 더 적은 정보만 포함 (제목 + 날짜 정도)
 * - 주로 메인 홈 최근 게시글, 관리자 대시보드 등에 활용
 *
 * 사용 위치
 * 메인 홈 최근 공지사항
 * 관리자 페이지 게시글 요약
 */
@Getter
public class BoardSummaryResponse {

    /** 게시글 번호 */
    private Long boardId;

    /** 게시글 유형 */
    private String type;

    /** 제목 */
    private String title;

    /** 작성일시 */
    private LocalDateTime createdAt;

    /** 조회수 */
    private int viewCount;

    /**
     * Board Entity → BoardSummaryResponse 변환
     *
     * @param board Board Entity
     * @return BoardSummaryResponse
     */
    public static BoardSummaryResponse from(Board board) {
        BoardSummaryResponse res = new BoardSummaryResponse();
        res.boardId   = board.getBoardId();
        res.type      = board.getType();
        res.title     = board.getTitle();
        res.createdAt = board.getCreatedAt();
        res.viewCount = board.getViewCount();
        return res;
    }

    /**
     * 날짜 포맷
     * 예) "03-27" (메인 홈처럼 공간 좁은 곳)
     */
    public String getShortDate() {
        if (createdAt == null) return "";
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

    /**
     * 제목 말줄임 (30자 초과 시)
     * 예) "공지사항 제목이 너무 길면 이렇게 잘..."
     */
    public String getShortTitle() {
        if (title == null) return "";
        return title.length() > 30
                ? title.substring(0, 30) + "..."
                : title;
    }
}
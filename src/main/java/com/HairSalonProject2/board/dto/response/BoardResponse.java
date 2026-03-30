package com.HairSalonProject2.board.dto.response;

import com.HairSalonProject2.board.entity.Board;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BoardResponse — 게시글 목록용 응답 DTO
 * =====================================================
 * 게시글 목록 화면에서 필요한 정보만 골라서 담는 그릇
 * Entity(Board) 를 직접 화면에 넘기지 않고
 * 필요한 필드만 골라서 전달 (보안 + 성능)
 *
 * 포함 정보
 * - 게시글 번호, 제목, 작성자, 조회수, 작성일
 * - 목록에서 필요한 최소한의 정보만
 * - 본문 내용(content) 은 포함하지 않음 (상세 페이지에서만)
 *
 * 사용 위치
 * GET /boards/notices → 공지사항 목록
 * GET /boards/qna     → QnA 목록
 */
@Getter
public class BoardResponse {

    /** 게시글 번호 */
    private Long boardId;

    /** 게시글 유형 (NOTICE / QNA) */
    private String type;

    /** 게시글 제목 */
    private String title;

    /** 작성자 회원 ID */
    private String memberId;

    /** 조회수 */
    private int viewCount;

    /** 작성일시 */
    private LocalDateTime createdAt;

    /** 답글 여부 (parentId 가 있으면 true) */
    private boolean reply;

    /**
     * 답글 개수
     * 목록에서 제목 옆에 [2] 이런 식으로 표시
     * Service 에서 별도로 세팅해줌
     */
    private int replyCount;

    /**
     * Board Entity → BoardResponse 변환 메서드
     * Service 에서 이렇게 사용
     * BoardResponse response = BoardResponse.from(board);
     *
     * @param board Board Entity
     * @return BoardResponse
     */
    public static BoardResponse from(Board board) {
        BoardResponse res = new BoardResponse();
        res.boardId    = board.getBoardId();
        res.type       = board.getType();
        res.title      = board.getTitle();
        res.memberId   = board.getMemberId();
        res.viewCount  = board.getViewCount();
        res.createdAt  = board.getCreatedAt();
        res.reply      = !board.isOriginal();
        res.replyCount = 0; // 기본값 0, Service에서 세팅
        return res;
    }

    /**
     * 답글 개수 세팅
     * BoardService 에서 목록 조회 후 호출
     * board.setReplyCount(count);
     *
     * @param replyCount 답글 개수
     */
    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }

    /**
     * 작성일 포맷 변환
     * Thymeleaf 에서 ${board.formattedDate} 로 사용
     * 예) "2026-03-27"
     */
    public String getFormattedDate() {
        if (createdAt == null) return "";
        return createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
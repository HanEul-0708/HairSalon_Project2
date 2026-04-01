package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * BoardResponse
 * 게시글 목록용 응답 DTO
 */
@Getter
public class BoardResponse {

    /** 게시글 번호 */
    private Integer boardId;

    /** 게시판 타입 */
    private String type;

    /** 제목 */
    private String title;

    /** 작성자 아이디 */
    private String memberId;

    /** 조회수 */
    private Integer viewCount;

    /** 작성일시 */
    private LocalDateTime createdAt;

    /** 답글 여부 */
    private boolean reply;

    /** 답글 개수 */
    private long replyCount;

    /** 숨김 여부 */
    private boolean hidden;


    /** 신고 수 */
    private int reportCount;

    /** 답변 완료 여부 */
    private boolean answered;

    public BoardResponse() {
    }

    public BoardResponse(Integer boardId,
                         BoardType type,
                         String title,
                         String memberId,
                         Integer viewCount,
                         LocalDateTime createdAt,
                         boolean reply,
                         Long replyCount,
                         Boolean hidden,
                         Integer reportCount,
                         Boolean answered) {
        this.boardId = boardId;
        this.type = type != null ? type.name() : null;
        this.title = title;
        this.memberId = memberId;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.reply = reply;
        this.replyCount = replyCount == null ? 0L : replyCount;
        this.hidden = hidden != null && hidden;
        this.reportCount = reportCount == null ? 0 : reportCount;
        this.answered = answered != null && answered;
    }

    public static BoardResponse from(Board board) {
        return new BoardResponse(
                board.getBoardId(),
                board.getType(),
                board.getTitle(),
                board.getMember().getMemberId(),
                board.getViewCount(),
                board.getCreatedAt(),
                !board.isOriginal(),
                0L,
                board.isHidden(),
                board.getReportCount() == null ? 0 : board.getReportCount(),
                !board.getChildren().isEmpty()
        );
    }

    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }
}

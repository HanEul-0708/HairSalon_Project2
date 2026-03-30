package com.HairSalonProject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BoardReplyRequest — 답글 작성 요청 DTO
 * =====================================================
 * 관리자가 QnA 문의에 답글 달 때 사용
 * parentId 필수 — 어느 글의 답글인지 반드시 알아야 함
 *
 * 사용 위치
 * POST /boards/qna/{boardId}/reply
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardReplyRequest {

    /**
     * 원글 boardId
     * 어느 게시글에 답글을 다는지 식별
     */
    @NotNull(message = "원글 정보가 필요합니다")
    private Long parentId;

    /**
     * 답글 제목
     * 보통 "Re: 원글 제목" 형태로 자동 생성하거나
     * 관리자가 직접 입력
     */
    @NotBlank(message = "제목을 입력해주세요")
    private String title;

    /**
     * 답글 내용
     */
    @NotBlank(message = "내용을 입력해주세요")
    private String content;
}
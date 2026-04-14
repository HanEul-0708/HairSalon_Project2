package com.hairsalonproject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BoardReplyRequest
 * 관리자 답글 작성 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardReplyRequest {

    /**
     * 부모 글 ID
     */
    @NotNull(message = "부모 글 번호는 필수입니다.")
    private Long parentId;

    /**
     * 답글 제목
     */
    @Size(max = 200, message = "답변 제목은 200자 이하로 입력해주세요.")
    private String title;

    /**
     * 답글 내용
     */
    @NotBlank(message = "답글 내용은 필수입니다.")
    @Size(max = 20000, message = "답변 내용은 20,000자 이하로 입력해주세요.")
    private String content;
}

package com.hairsalonproject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BoardCreateRequest
 * 원글 작성 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardCreateRequest {

    /**
     * 제목
     */
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    /**
     * 내용
     */
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 20000, message = "내용은 20,000자 이하로 입력해주세요.")
    private String content;
}

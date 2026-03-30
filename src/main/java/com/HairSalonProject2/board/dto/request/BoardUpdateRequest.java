package com.HairSalonProject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BoardUpdateRequest — 게시글 수정 요청 DTO
 * =====================================================
 * 사용자가 수정 폼에 입력한 값을 담는 그릇
 * 제목과 내용만 수정 가능
 * (타입, 작성자 등은 수정 불가)
 *
 * 사용 위치
 * POST /boards/{boardId}/edit
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardUpdateRequest {

    /**
     * 수정할 제목
     */
    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요")
    private String title;

    /**
     * 수정할 본문
     * Summernote 에디터에서 HTML 형태로 전송됨
     */
    @NotBlank(message = "내용을 입력해주세요")
    private String content;
}
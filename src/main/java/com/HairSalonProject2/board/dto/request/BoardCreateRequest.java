package com.HairSalonProject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BoardCreateRequest — 게시글 작성 요청 DTO
 * =====================================================
 * 사용자가 글쓰기 폼에 입력한 값을 담는 그릇
 * Controller 에서 @Valid 와 함께 사용
 *
 * 사용 위치
 * POST /boards/qna      → QnA 작성
 * POST /boards/notices  → 공지사항 작성 (ADMIN 전용)
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardCreateRequest {

    /**
     * 게시글 유형
     * "NOTICE" 또는 "QNA"
     * 히든 필드로 전송됨
     */
    @NotBlank(message = "게시글 유형을 선택해주세요")
    private String type;

    /**
     * 게시글 제목
     * @NotBlank → 빈 칸 제출 방지
     * @Size     → 200자 이내 제한
     */
    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요")
    private String title;

    /**
     * 게시글 본문
     * Summernote 에디터에서 HTML 형태로 전송됨
     * 예) <p>안녕하세요</p><img src="...">
     */
    @NotBlank(message = "내용을 입력해주세요")
    private String content;

    /**
     * 부모 게시글 ID (답글인 경우)
     * 원글 작성 시 null
     * 답글 작성 시 원글의 boardId
     * @Setter 가 있어야 폼 hidden 값이 바인딩됨
     */
    private Long parentId;
}
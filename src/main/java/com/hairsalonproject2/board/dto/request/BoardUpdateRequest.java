package com.hairsalonproject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * BoardUpdateRequest
 * 게시글 수정 요청 데이터
 */
@Getter
@Setter
public class BoardUpdateRequest {

    /**
     * 제목
     */
    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 200, message = "제목은 200자 이하이어야 합니다.")
    private String title;

    /**
     * 내용
     */
    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    /**
     * 수정 화면에서 삭제할 첨부파일 번호 목록
     * 체크박스 name="deleteFileIds" 로 들어온다.
     */
    private List<Long> deleteFileIds = new ArrayList<>();
}

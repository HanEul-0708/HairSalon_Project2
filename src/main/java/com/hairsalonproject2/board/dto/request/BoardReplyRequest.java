package com.hairsalonproject2.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 private String title;

 /**
  * 답글 내용
  */
 @NotBlank(message = "답글 내용은 필수입니다.")
 private String content;
}
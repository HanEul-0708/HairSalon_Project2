package com.hairsalonproject2.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ReviewUpdateRequest
 *
 * 리뷰 수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class ReviewUpdateRequest {

    /**
     * 평점
     */
    @NotNull(message = "평점은 필수입니다.")
    @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5 이하여야 합니다.")
    private Byte rating;

    /**
     * 리뷰 내용
     */
    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(max = 1000, message = "리뷰 내용은 1,000자 이하로 입력해주세요.")
    private String content;
}

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
 * ReviewCreateRequest
 *
 * 리뷰 작성 요청 DTO
 *
 * [중요]
 * 예전에는 memberId, designerId도 같이 받았지만
 * 이제는 reservationId만 받고
 * 회원/디자이너 정보는 예약에서 직접 가져오도록 바꾼다.
 *
 * 이유:
 * - 잘못된 회원 ID, 디자이너 ID가 들어오는 것을 막기 위해
 * - 예약 정보와 리뷰 정보의 일관성을 지키기 위해
 */
@Getter
@Setter
@NoArgsConstructor
public class ReviewCreateRequest {

    /**
     * 예약 ID
     *
     * 어떤 예약에 대한 리뷰인지 식별하는 값
     */
    @NotNull(message = "예약 ID는 필수입니다.")
    private Integer reservationId;

    /**
     * 평점
     *
     * 1점 ~ 5점만 허용
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

package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * ReviewResponse
 *
 * 리뷰 응답 DTO
 *
 * 클라이언트에게 반환되는 리뷰 정보
 */
@Getter
@AllArgsConstructor
public class ReviewResponse {

    /**
     * 리뷰 ID
     */
    private Integer reviewId;

    /**
     * 예약 ID
     */
    private Integer reservationId;

    /**
     * 회원 ID
     */
    private String memberId;

    /**
     * 회원 이름
     *
     * 추가 이유:
     * 프론트에서 회원 이름 표시를 위해 사용
     */
    private String memberName;

    /**
     * 디자이너 ID
     */
    private Integer designerId;

    /**
     * 디자이너 이름
     *
     * 추가 이유:
     * 리뷰 목록에서 디자이너 이름 표시
     */
    private String designerName;

    /**
     * 평점
     */
    private Byte rating;

    /**
     * 리뷰 내용
     */
    private String content;

    /**
     * 디자이너 답글 내용
     */
    private String replyContent;

    /**
     * 답글 작성일시
     */
    private LocalDateTime replyCreatedAt;

    /**
     * 리뷰 작성일시
     */
    private LocalDateTime createdAt;
}
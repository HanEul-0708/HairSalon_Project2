package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * ReviewResponse
 *
 * 리뷰 응답 DTO
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
     * 디자이너 ID
     */
    private Integer designerId;

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
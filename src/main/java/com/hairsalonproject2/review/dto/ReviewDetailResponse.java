package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ReviewDetailResponse
 *
 * 리뷰 상세 조회 응답 DTO
 *
 * 기본 리뷰 정보 + 이미지 목록까지 함께 내려주는 DTO
 */
@Getter
@AllArgsConstructor
public class ReviewDetailResponse {

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
     */
    private String memberName;

    /**
     * 디자이너 ID
     */
    private Integer designerId;

    /**
     * 디자이너 이름
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

    /**
     * 리뷰 이미지 목록
     */
    private List<ReviewImageResponse> images;
}
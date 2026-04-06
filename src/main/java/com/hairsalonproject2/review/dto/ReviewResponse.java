package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 리뷰 응답 DTO
 */
@Getter
@AllArgsConstructor
public class ReviewResponse {

    private Integer reviewId;
    private Integer reservationId;
    private String memberId;
    private String memberName;
    private Integer designerId;
    private String designerName;
    private String salonName;
    private String serviceName;
    private Byte rating;
    private String content;
    private String replyContent;
    private LocalDateTime replyCreatedAt;
    private LocalDateTime createdAt;
}

package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewDetailResponse {

    private Integer reviewId;
    private Integer reservationId;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String memberId;
    private String memberName;
    private Integer designerId;
    private String designerName;
    private String serviceName;
    private Byte rating;
    private String content;
    private String replyContent;
    private LocalDateTime replyCreatedAt;
    private LocalDateTime createdAt;
    private Integer likeCount;
    private boolean likedByCurrentUser;
    private List<ReviewImageResponse> images;
}

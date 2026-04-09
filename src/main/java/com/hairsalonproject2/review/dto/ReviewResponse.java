package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class ReviewResponse {

    private Integer reviewId;
    private Integer reservationId;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
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
    private String thumbnailImageUrl;
    private Integer likeCount;
    private boolean likedByCurrentUser;
}

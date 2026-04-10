package com.hairsalonproject2.home.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HomeReviewCardResponse {
    private Integer reviewId;
    private String maskedMemberName;
    private String salonName;
    private String serviceName;
    private int rating;
    private String content;
    private String createdDate;
}

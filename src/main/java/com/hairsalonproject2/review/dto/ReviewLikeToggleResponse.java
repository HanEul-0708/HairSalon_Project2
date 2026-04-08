package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewLikeToggleResponse {

    private Integer reviewId;
    private Integer likeCount;
    private boolean liked;
}

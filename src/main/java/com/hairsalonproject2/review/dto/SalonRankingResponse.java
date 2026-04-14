package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SalonRankingResponse {

    private Integer salonId;
    private String salonName;
    private Double averageRating;
    private Long reviewCount;
}

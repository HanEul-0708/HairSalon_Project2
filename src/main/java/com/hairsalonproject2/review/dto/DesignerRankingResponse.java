package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DesignerRankingResponse {

    private Integer designerId;
    private String designerName;
    private String salonName;
    private Integer careerYears;
    private Double averageRating;
    private Long reviewCount;
}

package com.hairsalonproject2.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalonRankResponse {
    private Integer rank;
    private Integer salonId;
    private String salonName;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
}

package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalonRankResponse {
    private Integer rank;
    private Integer salonId;
    private String salonName;
    private String address;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
}

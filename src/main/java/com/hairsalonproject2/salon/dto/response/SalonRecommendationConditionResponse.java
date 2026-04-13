package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SalonRecommendationConditionResponse {
    private Integer salonId;
    private String salonName;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
    private List<String> reviewKeywords;
}

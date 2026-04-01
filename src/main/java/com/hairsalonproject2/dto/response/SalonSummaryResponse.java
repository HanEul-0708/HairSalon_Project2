package com.hairsalonproject2.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalonSummaryResponse {
    private Integer salonId;
    private String name;
    private String address;
    private String phone;
    private String imageUrl;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
    private Boolean reservable;
    private Double distanceKm;
}

package com.hairsalonproject2.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SalonDetailResponse {
    private Integer salonId;
    private String externalId;
    private String sourceType;
    private String name;
    private String address;
    private String roadAddress;
    private String phone;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String imageUrl;
    private String placeUrl;
    private Boolean reservable;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
    private List<DesignerSummaryResponse> designers;
    private List<SalonServiceSummaryResponse> services;
}

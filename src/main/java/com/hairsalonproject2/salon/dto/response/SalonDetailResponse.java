package com.hairsalonproject2.salon.dto.response;

import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
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
    private String imageUrl;
    private String placeUrl;
    private Boolean reservable;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer likeCount;
    private List<DesignerSummaryResponse> designers;
    private List<SalonServiceSummaryResponse> services;
}

package com.hairsalonproject2.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DesignerDetailResponse {
    private Integer designerId;
    private Integer salonId;
    private String salonName;
    private String memberId;
    private String name;
    private String profileImage;
    private String introduction;
    private Integer careerYears;
    private BigDecimal averageRating;
    private Long reviewCount;
    private List<SalonServiceSummaryResponse> salonServices;
}

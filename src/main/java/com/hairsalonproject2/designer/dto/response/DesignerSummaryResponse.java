package com.hairsalonproject2.designer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DesignerSummaryResponse {
    private Integer designerId;
    private Integer salonId;
    private String salonName;
    private String name;
    private String profileImage;
    private Integer careerYears;
    private BigDecimal averageRating;
    private Long reviewCount;
    private Integer likeCount;
}

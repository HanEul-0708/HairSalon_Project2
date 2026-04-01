package com.hairsalonproject2.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SalonSearchRequest {
    private String keyword;
    private String region;
    private BigDecimal minRating;
    private Boolean reservable;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double radiusKm;
    private String sort = "recommended";
}

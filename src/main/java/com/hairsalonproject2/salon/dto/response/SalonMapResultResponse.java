package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalonMapResultResponse {
    private Integer salonId;
    private String name;
    private String address;
    private String roadAddress;
    private String placeUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
}


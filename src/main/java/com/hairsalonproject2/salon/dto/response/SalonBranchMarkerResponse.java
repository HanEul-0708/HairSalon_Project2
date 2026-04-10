package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalonBranchMarkerResponse {
    private Integer salonId;
    private String name;
    private String address;
    private String roadAddress;
    private String phone;
    private String detailUrl;
    private BigDecimal longitude;
    private BigDecimal latitude;
}

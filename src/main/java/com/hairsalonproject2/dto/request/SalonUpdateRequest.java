package com.hairsalonproject2.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SalonUpdateRequest {
    private String name;

    private String address;

    private String roadAddress;
    private String phone;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String imageUrl;
    private String placeUrl;
    private Boolean reservable = true;
}

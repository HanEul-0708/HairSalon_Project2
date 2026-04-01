package com.hairsalonproject2.integration.kakao;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KakaoPlaceSearchResult {
    private String externalId;
    private String placeName;
    private String addressName;
    private String roadAddressName;
    private String phone;
    private String placeUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
}

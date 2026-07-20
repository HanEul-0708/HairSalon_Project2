package com.hairsalonproject2.common.integration.kakao;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder(toBuilder = true)
public class KakaoPlaceSearchResult {
 private String externalId;
 private String placeName;
 private String addressName;
 private String roadAddressName;
 private String phone;
 private String placeUrl;
 private String thumbnailUrl;
 private BigDecimal longitude;
 private BigDecimal latitude;
 private BigDecimal averageRating;
 private Integer reviewCount;
}

package com.hairsalonproject2.common.integration.kakao;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KakaoAddressSearchResult {
 private String addressName;
 private String roadAddressName;
 private BigDecimal longitude;
 private BigDecimal latitude;
}

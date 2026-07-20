package com.hairsalonproject2.salonservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ServicePriceCompareResponse {
 private Integer serviceId;
 private Integer salonId;
 private String salonName;
 private String address;
 private String serviceName;
 private Integer price;
 private Integer duration;
 private BigDecimal averageRating;
}
package com.hairsalonproject2.salonservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SalonServiceDetailResponse {
 private Integer serviceId;
 private Integer salonId;
 private String salonName;
 private String name;
 private Integer price;
 private Integer duration;
 private String description;
}
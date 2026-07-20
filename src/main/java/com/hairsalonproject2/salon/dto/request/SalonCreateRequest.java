package com.hairsalonproject2.salon.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonCreateRequest {
 private String externalId;
 private String sourceType;
 private String name;
 private String address;
 private String roadAddress;
 private String phone;
 private String description;
 private String imageUrl;
 private String placeUrl;
 private Boolean reservable = true;
}

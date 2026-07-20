package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class SalonSummaryResponse {
 private Integer salonId;
 private String name;
 private String address;
 private String roadAddress;
 private String phone;
 private String imageUrl;
 private BigDecimal averageRating;
 private Integer reviewCount;
 private Integer likeCount;
 private Boolean reservable;
 private LocalDateTime createdAt;
}

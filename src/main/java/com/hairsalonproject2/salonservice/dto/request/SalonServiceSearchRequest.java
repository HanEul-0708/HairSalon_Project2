package com.hairsalonproject2.salonservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonServiceSearchRequest {
 private String keyword;
 private String salonKeyword;
 private String region;
 @Min(value = 0, message = "최대 가격은 0원 이상으로 입력해 주세요.")
 private Integer maxPrice;
 @Min(value = 1, message = "최대 소요 시간은 1분 이상으로 입력해 주세요.")
 private Integer maxDuration;
 private String sortBy;
 private boolean searched;
 @NotNull(message = "페이지는 1 이상의 정수로 입력해 주세요.")
 @Min(value = 1, message = "페이지는 1 이상으로 입력해 주세요.")
 private Integer page = 1;

 public boolean hasSearchRequest() {
  return searched;
 }
}

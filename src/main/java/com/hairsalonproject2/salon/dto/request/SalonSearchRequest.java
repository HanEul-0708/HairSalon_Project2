package com.hairsalonproject2.salon.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SalonSearchRequest {
 private String keyword;
 private String region;
 @DecimalMin(value = "0", message = "최소 평점은 0부터 5 사이의 숫자로 입력해 주세요.")
 @DecimalMax(value = "5", message = "최소 평점은 0부터 5 사이의 숫자로 입력해 주세요.")
 private BigDecimal minRating;
 @Min(value = 1, message = "페이지는 1 이상의 정수로 입력해 주세요.")
 private Integer page = 1;
 private Boolean reservable;
 private String sort = "recommended";
 private boolean serviceKeywordSearchEnabled;
 private boolean searched;

 public boolean hasSearchRequest() {
  return searched;
 }
}

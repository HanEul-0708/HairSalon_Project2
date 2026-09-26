package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Getter
@Setter
public class DesignerSearchRequest {
 private String keyword;
 private String salonKeyword;
 @DecimalMin(value = "0", message = "최소 평점은 0 이상으로 입력해 주세요.")
 @DecimalMax(value = "5", message = "최소 평점은 5 이하로 입력해 주세요.")
 private BigDecimal minRating;
 @Min(value = 0, message = "최소 경력은 0 이상으로 입력해 주세요.")
 private Integer minCareerYears;
 @Min(value = 0, message = "최소 리뷰 수는 0 이상으로 입력해 주세요.")
 private Long minReviewCount;
 @NotNull(message = "페이지 번호를 입력해 주세요.")
 @Min(value = 1, message = "페이지 번호는 1 이상으로 입력해 주세요.")
 private Integer page = 1;
 private String sortBy;
 private boolean searched;

 public boolean hasSearchRequest() {
  return searched;
 }
}

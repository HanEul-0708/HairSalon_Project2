package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DesignerSearchRequest {
 private String keyword;
 private String salonKeyword;
 private BigDecimal minRating;
 private Integer minCareerYears;
 private Long minReviewCount;
 private String sortBy;
 private boolean searched;

 public boolean hasSearchRequest() {
  return searched;
 }
}

package com.hairsalonproject2.designer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DesignerAiRecommendationItemResponse {
 private Integer designerId;
 private Integer salonId;
 private String designerName;
 private String salonName;
 private Integer careerYears;
 private BigDecimal averageRating;
 private Long reviewCount;
 private Integer likeCount;
 private Double score;
 private String reason;
 private List<String> tags;

 public String getDesignerDetailUrl() {
  return "/designers/" + designerId;
 }

 public String getSalonDetailUrl() {
  return "/salons/" + salonId;
 }
}

package com.hairsalonproject2.designer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DesignerAiRecommendationResponse {
 private String query;
 private String summary;
 private String source;
 private boolean llmUsed;
 private List<DesignerAiRecommendationItemResponse> recommendations;

 public boolean hasRecommendations() {
  return recommendations != null && !recommendations.isEmpty();
 }
}

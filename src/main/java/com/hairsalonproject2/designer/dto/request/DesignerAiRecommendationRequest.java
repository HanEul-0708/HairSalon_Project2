package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesignerAiRecommendationRequest {
    private String query;
    private Integer limit = 3;
}

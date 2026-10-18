package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.hairsalonproject2.designer.validation.ValidDesignerAiQuery;

@Getter
@Setter
public class DesignerAiRecommendationRequest {
 @Size(max = 300, message = "추천 요청은 300자 이내로 입력해 주세요.")
 @ValidDesignerAiQuery
 private String query;
 @Min(value = 1, message = "추천 수는 1 이상으로 입력해 주세요.")
 private Integer limit;
}

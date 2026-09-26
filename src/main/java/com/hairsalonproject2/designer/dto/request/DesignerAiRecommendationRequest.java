package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
public class DesignerAiRecommendationRequest {
 @Size(max = 300, message = "추천 요청은 300자 이내로 입력해 주세요.")
 private String query;
 @NotNull(message = "추천 수를 선택해 주세요.")
 @Min(value = 1, message = "추천 수는 1 이상으로 입력해 주세요.")
 @Max(value = 6, message = "추천 수는 6 이하로 입력해 주세요.")
 private Integer limit = 3;
}

package com.hairsalonproject2.designer.llm;

import java.util.List;

public record DesignerAiLlmResult(String summary, List<DesignerAiLlmRecommendation> recommendations) {
}

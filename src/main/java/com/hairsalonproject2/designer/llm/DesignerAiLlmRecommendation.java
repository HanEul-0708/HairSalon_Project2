package com.hairsalonproject2.designer.llm;

import java.util.List;

public record DesignerAiLlmRecommendation(
        Integer designerId,
        String reason,
        List<String> tags
) {
}

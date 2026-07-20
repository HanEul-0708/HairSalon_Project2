package com.hairsalonproject2.designer.llm;

import java.math.BigDecimal;
import java.util.List;

public record DesignerAiCandidatePrompt(Integer designerId, Integer salonId, String name, String salonName,String specialty, Integer careerYears, BigDecimal averageRating,Long reviewCount, Integer likeCount, String introduction, List<String> services,List<String> reviewSnippets) {
}

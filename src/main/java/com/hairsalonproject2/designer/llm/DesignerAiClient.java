package com.hairsalonproject2.designer.llm;

import java.util.List;
import java.util.Optional;

public interface DesignerAiClient {
    Optional<DesignerAiLlmResult> recommend(String query, List<DesignerAiCandidatePrompt> candidates, int limit);
}

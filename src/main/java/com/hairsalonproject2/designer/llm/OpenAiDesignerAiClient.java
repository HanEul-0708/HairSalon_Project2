package com.hairsalonproject2.designer.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OpenAiDesignerAiClient implements DesignerAiClient {
    private static final int MAX_REASON_LENGTH = 240;
    private static final int MAX_TAG_COUNT = 4;
    private static final String SYSTEM_PROMPT = """
            You recommend hair designers for a salon booking service.
            Use only the provided candidate data.
            Return Korean JSON only, with this exact shape:
            {"summary":"short Korean summary","recommendations":[{"designerId":1,"reason":"Korean reason","tags":["tag"]}]}
            Do not include markdown, code fences, or extra keys.
            """;

    private final ObjectMapper objectMapper;

    @Value("${designer.ai.openai.enabled:true}")
    private boolean enabled;

    @Value("${designer.ai.openai.api-key:}")
    private String apiKey;

    @Value("${designer.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${designer.ai.openai.model:gpt-4o-mini}")
    private String model;

    @Override
    public Optional<DesignerAiLlmResult> recommend(String query,
                                                   List<DesignerAiCandidatePrompt> candidates,
                                                   int limit) {
        if (!enabled || !StringUtils.hasText(apiKey) || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "query", query,
                    "limit", limit,
                    "candidates", candidates
            ));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.2);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", "User request and candidates:\n" + payload)
            ));

            String responseBody = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            Set<Integer> validDesignerIds = new LinkedHashSet<>(
                    candidates.stream().map(DesignerAiCandidatePrompt::designerId).toList()
            );
            return parseResponse(responseBody, validDesignerIds, limit);
        } catch (JsonProcessingException | RestClientException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<DesignerAiLlmResult> parseResponse(String responseBody,
                                                       Set<Integer> validDesignerIds,
                                                       int limit) throws JsonProcessingException {
        if (!StringUtils.hasText(responseBody)) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (!StringUtils.hasText(content)) {
            return Optional.empty();
        }

        JsonNode contentNode = objectMapper.readTree(extractJson(content));
        String summary = trimToNull(contentNode.path("summary").asText(null));
        JsonNode recommendationNode = contentNode.path("recommendations");
        if (!recommendationNode.isArray()) {
            return Optional.empty();
        }

        List<DesignerAiLlmRecommendation> recommendations = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (JsonNode item : recommendationNode) {
            if (recommendations.size() >= limit) {
                break;
            }

            Integer designerId = item.path("designerId").canConvertToInt()
                    ? item.path("designerId").asInt()
                    : null;
            if (designerId == null || !validDesignerIds.contains(designerId) || !seen.add(designerId)) {
                continue;
            }

            String reason = truncate(trimToNull(item.path("reason").asText(null)), MAX_REASON_LENGTH);
            List<String> tags = parseTags(item.path("tags"));
            recommendations.add(new DesignerAiLlmRecommendation(designerId, reason, tags));
        }

        if (recommendations.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new DesignerAiLlmResult(summary, recommendations));
    }

    private List<String> parseTags(JsonNode tagsNode) {
        if (!tagsNode.isArray()) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : tagsNode) {
            if (tags.size() >= MAX_TAG_COUNT) {
                break;
            }
            String tag = truncate(trimToNull(tagNode.asText(null)), 24);
            if (tag != null && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}

package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationItemResponse;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.llm.DesignerAiLlmRecommendation;
import com.hairsalonproject2.designer.llm.DesignerAiLlmResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.hairsalonproject2.designer.service.DesignerAiQueryParser.ParsedQuery;
import static com.hairsalonproject2.designer.service.DesignerAiQueryParser.normalizeText;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DesignerAiRecommendationService {
 private static final int LLM_CANDIDATE_WINDOW = 8;

 private final DesignerAiCandidateService candidateService;
 private final DesignerAiClient designerAiClient;

 public DesignerAiRecommendationResponse recommend(DesignerAiRecommendationRequest request) {
  DesignerAiRecommendationRequest safeRequest = request == null ? new DesignerAiRecommendationRequest() : request;
  return recommend(safeRequest.getQuery(), safeRequest.getLimit());
 }

 public DesignerAiRecommendationResponse recommend(String query, Integer limit) {
  String safeQuery = normalizeQuery(query);
  if (!StringUtils.hasText(safeQuery))
   return emptyResponse(query, "원하는 스타일, 분위기, 경력 조건을 문장으로 입력하면 AI가 디자이너를 추천합니다.");
  ParsedQuery intent = DesignerAiQueryParser.parse(safeQuery);
  if (intent.validationError() != null) throw new IllegalArgumentException(intent.validationError());
  if (limit != null && limit < 1) throw new IllegalArgumentException("추천 수는 1 이상으로 입력해 주세요.");
  List<DesignerAiCandidatePrompt> candidates = candidateService.loadCandidates();
  if (candidates.isEmpty())
   return emptyResponse(safeQuery, "추천할 수 있는 디자이너 데이터가 아직 없습니다.");
  List<ScoredDesignerCandidate> scoredCandidates = candidates.stream().filter(candidate -> matchesConditions(candidate, intent))
   .collect(Collectors.toMap(DesignerAiCandidatePrompt::designerId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new))
   .values().stream().map(candidate -> new ScoredDesignerCandidate(candidate, calculateScore(candidate, intent)))
   .sorted(Comparator.comparing(ScoredDesignerCandidate::score).reversed().thenComparing(candidate -> candidate.prompt().name(), String.CASE_INSENSITIVE_ORDER).thenComparing(candidate -> candidate.prompt().designerId())).toList();
  if (scoredCandidates.isEmpty()) return emptyResponse(safeQuery, "입력한 조건에 맞는 디자이너가 없습니다. 조건을 바꿔 다시 입력해 주세요.");
  int safeLimit = intent.outputLimit(limit, scoredCandidates.size());
  List<DesignerAiCandidatePrompt> llmCandidates = scoredCandidates.stream().limit(LLM_CANDIDATE_WINDOW).map(ScoredDesignerCandidate::prompt).toList();
  DesignerAiLlmResult rawLlmResult = designerAiClient.recommend(safeQuery, llmCandidates, Math.min(safeLimit, llmCandidates.size())).orElse(null);
  DesignerAiLlmResult llmResult = sanitizeLlmResult(rawLlmResult, llmCandidates, safeLimit);
  List<ScoredDesignerCandidate> selected = selectCandidates(scoredCandidates, llmResult, safeLimit);
  Map<Integer, DesignerAiLlmRecommendation> llmRecommendationByDesignerId = llmResult == null ? Map.of() : llmResult.recommendations().stream().collect(Collectors.toMap(DesignerAiLlmRecommendation::designerId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
  List<DesignerAiRecommendationItemResponse> items = selected.stream().map(candidate -> toResponseItem(candidate, intent, llmRecommendationByDesignerId.get(candidate.prompt().designerId()))).toList();
  boolean llmUsed = llmResult != null;
  // The server knows the final count, including candidates appended after an LLM partial result.
  String summary = buildFallbackSummary(safeQuery, items.size(), intent);
  return DesignerAiRecommendationResponse.builder().query(safeQuery).summary(summary).source(llmUsed ? "LLM" : "LOCAL_RULES").llmUsed(llmUsed).recommendations(items).build(); }

 private boolean matchesConditions(DesignerAiCandidatePrompt candidate, ParsedQuery intent) {
  BigDecimal career = candidate.careerYears() == null ? null : BigDecimal.valueOf(candidate.careerYears());
  if (!intent.careerConstraints().stream().allMatch(condition -> condition.matches(career))) return false;
  if (!intent.ratingConstraints().stream().allMatch(condition -> condition.matches(candidate.averageRating()))) return false;
  if (intent.excludedStyles().stream().anyMatch(style -> matchesStyle(candidate, style))) return false;
  if (intent.styles().isEmpty()) return true;
  return intent.stylesAny() ? intent.styles().stream().anyMatch(style -> matchesStyle(candidate, style))
   : intent.styles().stream().allMatch(style -> matchesStyle(candidate, style));
 }

 private DesignerAiLlmResult sanitizeLlmResult(DesignerAiLlmResult result, List<DesignerAiCandidatePrompt> candidates, int limit) {
  if (result == null || result.recommendations() == null) return null;
  Set<Integer> validIds = candidates.stream().map(DesignerAiCandidatePrompt::designerId).collect(Collectors.toSet());
  Set<Integer> seen = new HashSet<>();
  List<DesignerAiLlmRecommendation> valid = result.recommendations().stream().filter(Objects::nonNull)
   .filter(item -> validIds.contains(item.designerId()) && seen.add(item.designerId())).limit(Math.min(limit, LLM_CANDIDATE_WINDOW)).toList();
  return valid.isEmpty() ? null : new DesignerAiLlmResult(result.summary(), valid);
 }

 private double calculateScore(DesignerAiCandidatePrompt candidate, ParsedQuery intent) {
  double score = 0;
  double averageRating = candidate.averageRating() == null ? 0.0 : candidate.averageRating().doubleValue();
  long reviewCount = candidate.reviewCount() == null ? 0L : candidate.reviewCount();
  int careerYears = safeInteger(candidate.careerYears());
  int likeCount = safeInteger(candidate.likeCount());
  score += averageRating * 12.0;
  score += Math.log1p(reviewCount) * 5.0;
  score += Math.min(careerYears, 20) * 1.4;
  score += Math.log1p(likeCount) * 2.2;
  String haystack = buildCandidateHaystack(candidate);
  for (String token : intent.tokens()) {
   if (token.length() < 2) continue;
   if (haystack.contains(token)) score += token.length() >= 4 ? 8.0 : 5.0;
  }
  for (String style : intent.styles()) if (matchesStyle(candidate, style)) score += 18.0;
  if (intent.wantsExperienced()) score += Math.min(careerYears, 20) * 1.2;
  if (intent.wantsReviewProven()) score += Math.log1p(reviewCount) * 4.0;
  if (intent.wantsPopular()) score += Math.log1p(likeCount) * 4.0;
  return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
 }

 private List<ScoredDesignerCandidate> selectCandidates(List<ScoredDesignerCandidate> scoredCandidates, DesignerAiLlmResult llmResult, int limit) {
  if (llmResult == null || llmResult.recommendations() == null || llmResult.recommendations().isEmpty())
   return scoredCandidates.stream().limit(limit).toList();
  Map<Integer, ScoredDesignerCandidate> byDesignerId = scoredCandidates.stream().collect(Collectors.toMap(candidate -> candidate.prompt().designerId(), Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
  List<ScoredDesignerCandidate> selected = new ArrayList<>();
  Set<Integer> seen = new LinkedHashSet<>();
  for (DesignerAiLlmRecommendation recommendation : llmResult.recommendations()) {
   ScoredDesignerCandidate candidate = byDesignerId.get(recommendation.designerId());
   if (candidate != null && seen.add(candidate.prompt().designerId())) selected.add(candidate);
   if (selected.size() >= limit) return selected;
  }
  for (ScoredDesignerCandidate candidate : scoredCandidates) {
   if (seen.add(candidate.prompt().designerId())) selected.add(candidate);
   if (selected.size() >= limit) break;
  }
  return selected;
 }

 private DesignerAiRecommendationItemResponse toResponseItem(ScoredDesignerCandidate scoredCandidate, ParsedQuery intent, DesignerAiLlmRecommendation llmRecommendation) {
  DesignerAiCandidatePrompt candidate = scoredCandidate.prompt();
  String reason = llmRecommendation != null && StringUtils.hasText(llmRecommendation.reason()) ? llmRecommendation.reason() : buildFallbackReason(candidate, intent);
  List<String> tags = llmRecommendation != null && llmRecommendation.tags() != null && !llmRecommendation.tags().isEmpty() ? llmRecommendation.tags() : buildFallbackTags(candidate, intent);
  return DesignerAiRecommendationItemResponse.builder().designerId(candidate.designerId()).salonId(candidate.salonId()).designerName(candidate.name()).salonName(candidate.salonName()).careerYears(candidate.careerYears()).averageRating(candidate.averageRating()).reviewCount(candidate.reviewCount()).likeCount(candidate.likeCount()).score(scoredCandidate.score()).reason(reason).tags(tags).build();
 }

 private String buildFallbackSummary(String query, int count, ParsedQuery intent) {
  if (!intent.styles().isEmpty())
   return "'" + query + "' 요청에서 감지한 시술/스타일 키워드와 평점, 리뷰, 경력을 함께 반영해 " + count + "명의 디자이너를 추천했습니다.";
  return "'" + query + "' 요청을 기준으로 소개, 소속 미용실, 평점, 리뷰, 경력 데이터를 조합해 " + count + "명의 디자이너를 추천했습니다.";
 }

 private String buildFallbackReason(DesignerAiCandidatePrompt candidate, ParsedQuery intent) {
  List<String> reasons = new ArrayList<>();
  if (!intent.styles().isEmpty() && intent.styles().stream().anyMatch(style -> matchesStyle(candidate, style)))
   reasons.add("요청한 스타일과 디자이너의 전문 분야·소개가 잘 맞습니다.");
  if (candidate.averageRating() != null && candidate.averageRating().compareTo(BigDecimal.valueOf(4.0)) >= 0)
   reasons.add("평점이 높아 만족도 기준에 유리합니다.");
  if (candidate.reviewCount() != null && candidate.reviewCount() >= 5) reasons.add("리뷰 데이터가 충분해 선택 근거가 비교적 안정적입니다.");
  if (safeInteger(candidate.careerYears()) >= 5) reasons.add("경력 연차가 있어 안정적인 시술을 기대할 수 있습니다.");
  if (reasons.isEmpty()) reasons.add("요청 문장과 디자이너 소개, 소속 미용실 정보를 종합했을 때 우선 검토할 만합니다.");
  return String.join(" ", reasons);
 }

 private List<String> buildFallbackTags(DesignerAiCandidatePrompt candidate, ParsedQuery intent) {
  LinkedHashSet<String> tags = new LinkedHashSet<>();
  for (String style : intent.styles()) if (matchesStyle(candidate, style)) tags.add(styleLabel(style));
  if (candidate.averageRating() != null && candidate.averageRating().compareTo(BigDecimal.ZERO) > 0)
   tags.add("평점 " + candidate.averageRating().stripTrailingZeros().toPlainString());
  if (candidate.reviewCount() != null && candidate.reviewCount() > 0) tags.add("리뷰 " + candidate.reviewCount());
  if (safeInteger(candidate.careerYears()) > 0) tags.add("경력 " + candidate.careerYears() + "년");
  if (tags.isEmpty()) tags.add("AI 추천");
  return tags.stream().limit(4).toList();
 }

 private boolean matchesStyle(DesignerAiCandidatePrompt candidate, String style) {
  if (style == null) return false;
  if (style.equalsIgnoreCase(candidate.specialty())) return true;
  return DesignerAiQueryParser.hasPositiveStyleMention(candidate.introduction(), style);
 }

 private String buildCandidateHaystack(DesignerAiCandidatePrompt candidate) {
  return normalizeText(String.join(" ", nullToEmpty(candidate.name()), nullToEmpty(candidate.salonName()), nullToEmpty(candidate.specialty()), nullToEmpty(candidate.introduction()), String.join(" ", candidate.reviewSnippets() == null ? List.of() : candidate.reviewSnippets())));
 }

 private String styleLabel(String style) {
  return switch (style) {
   case "CUT" -> "커트";
   case "PERM" -> "펌";
   case "COLOR" -> "염색";
   case "STYLING" -> "스타일링";
   default -> style;
  };
 }

 private DesignerAiRecommendationResponse emptyResponse(String query, String summary) {
  return DesignerAiRecommendationResponse.builder().query(query).summary(summary).source("LOCAL_RULES").llmUsed(false).recommendations(List.of()).build();
 }

 private String normalizeQuery(String query) {
  if (!StringUtils.hasText(query)) return "";
  return query.trim();
 }

 private static String nullToEmpty(String value) {
  return value == null ? "" : value;
 }

 private static int safeInteger(Integer value) {
  return value == null ? 0 : value;
 }

 private record ScoredDesignerCandidate(DesignerAiCandidatePrompt prompt, double score) {
 }

}

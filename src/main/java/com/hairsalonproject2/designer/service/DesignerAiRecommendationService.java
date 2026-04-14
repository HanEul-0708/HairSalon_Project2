package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationItemResponse;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.llm.DesignerAiLlmRecommendation;
import com.hairsalonproject2.designer.llm.DesignerAiLlmResult;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignerAiRecommendationService {
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 6;
    private static final int LLM_CANDIDATE_WINDOW = 8;
    private static final int REVIEW_SNIPPET_LIMIT = 3;
    private static final int REVIEW_SNIPPET_LENGTH = 90;
    private static final Pattern CAREER_PATTERN = Pattern.compile("(\\d{1,2})\\s*(년|year|years)");
    private static final Pattern RATING_PATTERN = Pattern.compile("([1-5](?:\\.\\d)?)\\s*(점|star|stars)");
    private static final Set<String> STOP_WORDS = Set.of(
            "디자이너", "추천", "추천해줘", "추천해주세요", "찾아줘", "찾아주세요", "원해요", "원합니다",
            "미용실", "살롱", "헤어", "하는", "잘하는", "좋은", "있는", "받고", "싶어", "싶어요",
            "으로", "에서", "에게", "하고", "해주세요", "해줘", "좀"
    );
    private static final Map<String, List<String>> STYLE_KEYWORDS = Map.of(
            "CUT", List.of("커트", "컷", "단발", "레이어드", "앞머리", "남자", "남성", "숏컷", "헤어컷"),
            "PERM", List.of("펌", "파마", "볼륨", "매직", "셋팅", "웨이브", "다운펌", "디지털펌"),
            "COLOR", List.of("염색", "컬러", "탈색", "뿌리", "톤다운", "톤업", "브릿지", "하이라이트"),
            "STYLING", List.of("스타일링", "드라이", "업스타일", "웨딩", "면접", "행사", "고데기", "손질")
    );

    private final DesignerRepository designerRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ReviewRepository reviewRepository;
    private final DesignerAiClient designerAiClient;

    public DesignerAiRecommendationResponse recommend(DesignerAiRecommendationRequest request) {
        DesignerAiRecommendationRequest safeRequest = request == null
                ? new DesignerAiRecommendationRequest()
                : request;
        return recommend(safeRequest.getQuery(), safeRequest.getLimit());
    }

    public DesignerAiRecommendationResponse recommend(String query, Integer limit) {
        String safeQuery = normalizeQuery(query);
        int safeLimit = resolveLimit(limit);

        if (!StringUtils.hasText(safeQuery)) {
            return emptyResponse(query, "원하는 스타일, 분위기, 경력 조건을 문장으로 입력하면 AI가 디자이너를 추천합니다.");
        }

        List<DesignerAiCandidatePrompt> candidates = loadCandidates();
        if (candidates.isEmpty()) {
            return emptyResponse(safeQuery, "추천할 수 있는 디자이너 데이터가 아직 없습니다.");
        }

        DesignerSearchIntent intent = DesignerSearchIntent.from(safeQuery);
        List<ScoredDesignerCandidate> scoredCandidates = candidates.stream()
                .map(candidate -> new ScoredDesignerCandidate(candidate, calculateScore(candidate, intent)))
                .sorted(Comparator.comparing(ScoredDesignerCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.prompt().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<DesignerAiCandidatePrompt> llmCandidates = scoredCandidates.stream()
                .limit(LLM_CANDIDATE_WINDOW)
                .map(ScoredDesignerCandidate::prompt)
                .toList();
        DesignerAiLlmResult llmResult = designerAiClient.recommend(safeQuery, llmCandidates, safeLimit)
                .orElse(null);

        List<ScoredDesignerCandidate> selected = selectCandidates(scoredCandidates, llmResult, safeLimit);
        Map<Integer, DesignerAiLlmRecommendation> llmRecommendationByDesignerId = llmResult == null
                ? Map.of()
                : llmResult.recommendations().stream()
                .collect(Collectors.toMap(
                        DesignerAiLlmRecommendation::designerId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        List<DesignerAiRecommendationItemResponse> items = selected.stream()
                .map(candidate -> toResponseItem(candidate, intent, llmRecommendationByDesignerId.get(candidate.prompt().designerId())))
                .toList();

        boolean llmUsed = llmResult != null;
        String summary = llmUsed && StringUtils.hasText(llmResult.summary())
                ? llmResult.summary()
                : buildFallbackSummary(safeQuery, items.size(), intent);

        return DesignerAiRecommendationResponse.builder()
                .query(safeQuery)
                .summary(summary)
                .source(llmUsed ? "LLM" : "LOCAL_RULES")
                .llmUsed(llmUsed)
                .recommendations(items)
                .build();
    }

    private List<DesignerAiCandidatePrompt> loadCandidates() {
        Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows().stream()
                .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity(), (first, ignored) -> first));

        Map<Integer, List<String>> salonServicesCache = new LinkedHashMap<>();
        List<Designer> designers = designerRepository.findAll();
        List<DesignerAiCandidatePrompt> candidates = new ArrayList<>();

        for (Designer designer : designers) {
            if (designer.getSalon() == null || designer.getDesignerId() == null) {
                continue;
            }

            Integer salonId = designer.getSalon().getSalonId();
            List<String> services = salonServicesCache.computeIfAbsent(salonId, this::loadServiceTexts);
            DesignerRatingRow ratingRow = ratings.get(designer.getDesignerId());
            List<String> reviewSnippets = loadReviewSnippets(designer.getDesignerId());

            candidates.add(new DesignerAiCandidatePrompt(
                    designer.getDesignerId(),
                    salonId,
                    nullToEmpty(designer.getName()),
                    nullToEmpty(designer.getSalon().getName()),
                    designer.getSpecialty() == null ? "" : designer.getSpecialty().name(),
                    safeInteger(designer.getCareerYears()),
                    BigDecimal.valueOf(ratingRow == null || ratingRow.getAverageRating() == null ? 0.0 : ratingRow.getAverageRating())
                            .setScale(2, RoundingMode.HALF_UP),
                    ratingRow == null || ratingRow.getReviewCount() == null ? 0L : ratingRow.getReviewCount(),
                    safeInteger(designer.getLikeCount()),
                    nullToEmpty(designer.getIntroduction()),
                    services,
                    reviewSnippets
            ));
        }

        return candidates;
    }

    private List<String> loadServiceTexts(Integer salonId) {
        if (salonId == null) {
            return List.of();
        }
        return salonServiceRepository.findBySalonSalonId(salonId).stream()
                .map(this::serviceText)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .toList();
    }

    private String serviceText(SalonService service) {
        if (service == null) {
            return "";
        }
        String description = StringUtils.hasText(service.getDescription()) ? " - " + service.getDescription() : "";
        return nullToEmpty(service.getName()) + description;
    }

    private List<String> loadReviewSnippets(Integer designerId) {
        if (designerId == null) {
            return List.of();
        }
        return reviewRepository.findByDesigner_DesignerId(designerId).stream()
                .map(review -> abbreviate(review.getContent(), REVIEW_SNIPPET_LENGTH))
                .filter(StringUtils::hasText)
                .limit(REVIEW_SNIPPET_LIMIT)
                .toList();
    }

    private double calculateScore(DesignerAiCandidatePrompt candidate, DesignerSearchIntent intent) {
        double score = 0;
        double averageRating = candidate.averageRating() == null ? 0.0 : candidate.averageRating().doubleValue();
        long reviewCount = candidate.reviewCount() == null ? 0L : candidate.reviewCount();
        int careerYears = safeInteger(candidate.careerYears());
        int likeCount = safeInteger(candidate.likeCount());

        score += averageRating * 12.0;
        score += Math.log1p(reviewCount) * 5.0;
        score += Math.min(careerYears, 20) * 1.4;
        score += Math.log1p(likeCount) * 2.2;

        String haystack = normalizeText(String.join(" ",
                candidate.name(),
                candidate.salonName(),
                candidate.specialty(),
                candidate.introduction(),
                String.join(" ", candidate.services()),
                String.join(" ", candidate.reviewSnippets())
        ));

        for (String token : intent.tokens()) {
            if (token.length() < 2) {
                continue;
            }
            if (haystack.contains(token)) {
                score += token.length() >= 4 ? 8.0 : 5.0;
            }
        }

        for (String style : intent.styles()) {
            if (matchesStyle(candidate, haystack, style)) {
                score += 18.0;
            }
        }

        if (intent.minCareerYears() != null && careerYears < intent.minCareerYears()) {
            score -= 25.0;
        }
        if (intent.minRating() != null && averageRating < intent.minRating().doubleValue()) {
            score -= 22.0;
        }
        if (intent.wantsExperienced()) {
            score += Math.min(careerYears, 20) * 1.2;
        }
        if (intent.wantsReviewProven()) {
            score += Math.log1p(reviewCount) * 4.0;
        }
        if (intent.wantsPopular()) {
            score += Math.log1p(likeCount) * 4.0;
        }

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private List<ScoredDesignerCandidate> selectCandidates(List<ScoredDesignerCandidate> scoredCandidates,
                                                           DesignerAiLlmResult llmResult,
                                                           int limit) {
        if (llmResult == null || llmResult.recommendations() == null || llmResult.recommendations().isEmpty()) {
            return scoredCandidates.stream().limit(limit).toList();
        }

        Map<Integer, ScoredDesignerCandidate> byDesignerId = scoredCandidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.prompt().designerId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        List<ScoredDesignerCandidate> selected = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (DesignerAiLlmRecommendation recommendation : llmResult.recommendations()) {
            ScoredDesignerCandidate candidate = byDesignerId.get(recommendation.designerId());
            if (candidate != null && seen.add(candidate.prompt().designerId())) {
                selected.add(candidate);
            }
            if (selected.size() >= limit) {
                return selected;
            }
        }

        for (ScoredDesignerCandidate candidate : scoredCandidates) {
            if (seen.add(candidate.prompt().designerId())) {
                selected.add(candidate);
            }
            if (selected.size() >= limit) {
                break;
            }
        }

        return selected;
    }

    private DesignerAiRecommendationItemResponse toResponseItem(ScoredDesignerCandidate scoredCandidate,
                                                               DesignerSearchIntent intent,
                                                               DesignerAiLlmRecommendation llmRecommendation) {
        DesignerAiCandidatePrompt candidate = scoredCandidate.prompt();
        String reason = llmRecommendation != null && StringUtils.hasText(llmRecommendation.reason())
                ? llmRecommendation.reason()
                : buildFallbackReason(candidate, intent);
        List<String> tags = llmRecommendation != null && llmRecommendation.tags() != null && !llmRecommendation.tags().isEmpty()
                ? llmRecommendation.tags()
                : buildFallbackTags(candidate, intent);

        return DesignerAiRecommendationItemResponse.builder()
                .designerId(candidate.designerId())
                .salonId(candidate.salonId())
                .designerName(candidate.name())
                .salonName(candidate.salonName())
                .careerYears(candidate.careerYears())
                .averageRating(candidate.averageRating())
                .reviewCount(candidate.reviewCount())
                .likeCount(candidate.likeCount())
                .score(scoredCandidate.score())
                .reason(reason)
                .tags(tags)
                .build();
    }

    private String buildFallbackSummary(String query, int count, DesignerSearchIntent intent) {
        if (!intent.styles().isEmpty()) {
            return "'" + query + "' 요청에서 감지한 시술/스타일 키워드와 평점, 리뷰, 경력을 함께 반영해 "
                    + count + "명의 디자이너를 추천했습니다.";
        }
        return "'" + query + "' 요청을 기준으로 소개, 소속 미용실, 평점, 리뷰, 경력 데이터를 조합해 "
                + count + "명의 디자이너를 추천했습니다.";
    }

    private String buildFallbackReason(DesignerAiCandidatePrompt candidate, DesignerSearchIntent intent) {
        List<String> reasons = new ArrayList<>();
        if (!intent.styles().isEmpty() && intent.styles().stream().anyMatch(style -> matchesStyle(candidate, buildCandidateHaystack(candidate), style))) {
            reasons.add("요청한 스타일 키워드와 디자이너/시술 정보가 잘 맞습니다.");
        }
        if (candidate.averageRating() != null && candidate.averageRating().compareTo(BigDecimal.valueOf(4.0)) >= 0) {
            reasons.add("평점이 높아 만족도 기준에 유리합니다.");
        }
        if (candidate.reviewCount() != null && candidate.reviewCount() >= 5) {
            reasons.add("리뷰 데이터가 충분해 선택 근거가 비교적 안정적입니다.");
        }
        if (safeInteger(candidate.careerYears()) >= 5) {
            reasons.add("경력 연차가 있어 안정적인 시술을 기대할 수 있습니다.");
        }
        if (reasons.isEmpty()) {
            reasons.add("요청 문장과 디자이너 소개, 소속 미용실 정보를 종합했을 때 우선 검토할 만합니다.");
        }
        return String.join(" ", reasons);
    }

    private List<String> buildFallbackTags(DesignerAiCandidatePrompt candidate, DesignerSearchIntent intent) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String style : intent.styles()) {
            tags.add(styleLabel(style));
        }
        if (candidate.averageRating() != null && candidate.averageRating().compareTo(BigDecimal.ZERO) > 0) {
            tags.add("평점 " + candidate.averageRating().stripTrailingZeros().toPlainString());
        }
        if (candidate.reviewCount() != null && candidate.reviewCount() > 0) {
            tags.add("리뷰 " + candidate.reviewCount());
        }
        if (safeInteger(candidate.careerYears()) > 0) {
            tags.add("경력 " + candidate.careerYears() + "년");
        }
        if (tags.isEmpty()) {
            tags.add("AI 추천");
        }
        return tags.stream().limit(4).toList();
    }

    private boolean matchesStyle(DesignerAiCandidatePrompt candidate, String haystack, String style) {
        if (style == null) {
            return false;
        }
        if (style.equalsIgnoreCase(candidate.specialty())) {
            return true;
        }
        return STYLE_KEYWORDS.getOrDefault(style, List.of()).stream()
                .map(DesignerAiRecommendationService::normalizeText)
                .anyMatch(haystack::contains);
    }

    private String buildCandidateHaystack(DesignerAiCandidatePrompt candidate) {
        return normalizeText(String.join(" ",
                candidate.name(),
                candidate.salonName(),
                candidate.specialty(),
                candidate.introduction(),
                String.join(" ", candidate.services()),
                String.join(" ", candidate.reviewSnippets())
        ));
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
        return DesignerAiRecommendationResponse.builder()
                .query(query)
                .summary(summary)
                .source("LOCAL_RULES")
                .llmUsed(false)
                .recommendations(List.of())
                .build();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        return query.trim();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength).trim();
    }

    private record ScoredDesignerCandidate(DesignerAiCandidatePrompt prompt, double score) {
    }

    private record DesignerSearchIntent(
            String query,
            List<String> tokens,
            Set<String> styles,
            Integer minCareerYears,
            BigDecimal minRating,
            boolean wantsExperienced,
            boolean wantsReviewProven,
            boolean wantsPopular
    ) {
        private static DesignerSearchIntent from(String query) {
            String normalized = normalizeText(query);
            List<String> tokens = tokenize(normalized);
            Set<String> styles = detectStyles(normalized);

            return new DesignerSearchIntent(
                    query,
                    tokens,
                    styles,
                    extractCareerYears(normalized),
                    extractRating(normalized),
                    containsAny(normalized, List.of("경력", "베테랑", "숙련", "전문", "실력")),
                    containsAny(normalized, List.of("리뷰", "후기", "검증", "평점", "만족")),
                    containsAny(normalized, List.of("인기", "좋아요", "유명", "많이"))
            );
        }

        private static List<String> tokenize(String normalized) {
            if (!StringUtils.hasText(normalized)) {
                return List.of();
            }
            return List.of(normalized.split(" ")).stream()
                    .filter(StringUtils::hasText)
                    .filter(token -> token.length() >= 2)
                    .filter(token -> !STOP_WORDS.contains(token))
                    .distinct()
                    .toList();
        }

        private static Set<String> detectStyles(String normalized) {
            LinkedHashSet<String> styles = new LinkedHashSet<>();
            STYLE_KEYWORDS.forEach((style, keywords) -> {
                if (keywords.stream().map(DesignerAiRecommendationService::normalizeText).anyMatch(normalized::contains)) {
                    styles.add(style);
                }
            });
            return styles;
        }

        private static Integer extractCareerYears(String normalized) {
            if (!containsAny(normalized, List.of("경력", "년차", "베테랑", "숙련"))) {
                return null;
            }
            Matcher matcher = CAREER_PATTERN.matcher(normalized);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return null;
        }

        private static BigDecimal extractRating(String normalized) {
            Matcher matcher = RATING_PATTERN.matcher(normalized);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
            return null;
        }

        private static boolean containsAny(String text, List<String> keywords) {
            return keywords.stream().anyMatch(text::contains);
        }
    }
}

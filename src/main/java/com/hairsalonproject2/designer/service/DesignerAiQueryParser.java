package com.hairsalonproject2.designer.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The same deterministic interpretation is used for request validation and recommendation. */
public final class DesignerAiQueryParser {
 private static final Map<String, Integer> NATIVE_NUMBERS = nativeNumbers();
 private static final String NATIVE_PATTERN = NATIVE_NUMBERS.keySet().stream()
  .sorted(Comparator.comparingInt(String::length).reversed()).reduce((a, b) -> a + "|" + b).orElseThrow();
 private static final Pattern COUNT = Pattern.compile("(?<![\\d.,+-])([+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?|" + NATIVE_PATTERN + "|[영공일이삼사오육칠팔구십백천만억조]+)\\s*(명|분)");
 private static final Pattern CAREER = Pattern.compile("(최소|최대)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:년차?|years?)(?:\\s*(이상|이하|초과|미만))?");
 private static final Pattern RATING = Pattern.compile("(?:(평점|별점|rating)\\s*(?:은|는|이|가)?\\s*)?(최소|최대)?\\s*(\\d+(?:\\.\\d+)?)\\s*(점|stars?)?(?:\\s*(이상|이하|초과|미만))?");
 public static final Map<String, List<String>> STYLE_KEYWORDS = Map.of(
  "CUT", List.of("커트", "컷", "단발", "레이어드", "앞머리", "헤어컷"),
  "PERM", List.of("펌", "파마", "매직", "셋팅", "웨이브"),
  "COLOR", List.of("염색", "컬러", "탈색", "톤다운", "톤업", "브릿지", "하이라이트"),
  "STYLING", List.of("스타일링", "드라이", "업스타일", "고데기"));
 private static final Set<String> STOP_WORDS = Set.of("디자이너", "추천", "추천해줘", "추천해주세요", "찾아줘", "찾아주세요", "원해요", "원합니다", "미용실", "살롱", "헤어", "하는", "잘하는", "좋은", "있는", "받고", "싶어", "싶어요", "으로", "에서", "에게", "하고", "해주세요", "해줘", "좀", "추천인원", "추천수");

 private DesignerAiQueryParser() {}

 public static ParsedQuery parse(String query) {
  String text = Normalizer.normalize(query == null ? "" : query, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
  Matcher matcher = COUNT.matcher(text);
  BigInteger count = null;
  String error = null;
  StringBuilder scoringText = new StringBuilder(text);
  while (matcher.find()) {
   if (!isRecommendationCount(text, matcher)) continue;
   String expression = matcher.group(1);
   BigInteger value = parseCount(expression);
   if (value == null || value.signum() <= 0) error = "추천 인원은 1명 이상의 정수로 입력해 주세요.";
   else if (count != null && !count.equals(value)) error = "추천 인원은 하나로 정해 입력해 주세요.";
   else count = value;
   for (int i = matcher.start(); i < matcher.end(); i++) scoringText.setCharAt(i, ' ');
  }
  String scoreQuery = normalizeText(scoringText.toString());
  List<String> tokens = List.of(scoreQuery.split(" ")).stream().filter(t -> t.length() >= 2 && !STOP_WORDS.contains(t)).distinct().toList();
  List<NumericConstraint> careers = new ArrayList<>();
  Matcher career = CAREER.matcher(text);
  int previousCareerEnd = -1;
  while (career.find()) {
   String before = text.substring(0, career.start());
   String after = text.substring(career.end());
   boolean careerContext = career.group().contains("년차")
    || before.matches("(?s).*(?:경력|연차|career|experience)\\s*(?:은|는|이|가)?\\s*$")
    || after.matches("(?s)^\\s*(?:의\\s*)?(?:경력|연차|career|experience).*")
    || previousCareerEnd >= 0 && text.substring(previousCareerEnd, career.start()).matches("\\s*(?:에서|부터|그리고|및|이고|~|-)?\\s*");
   if (careerContext) {
    String bound = career.group(1) != null ? career.group(1) : before.matches("(?s).*최대\\s*(?:경력|연차|career|experience)\\s*(?:은|는|이|가)?\\s*$") ? "최대" : null;
    careers.add(new NumericConstraint(new BigDecimal(career.group(2)), comparison(career.group(3), bound)));
    previousCareerEnd = career.end();
   }
  }
  List<NumericConstraint> ratings = new ArrayList<>();
  Matcher rating = RATING.matcher(text);
  while (rating.find()) {
   if (rating.group(1) != null || rating.group(4) != null) {
    String before = text.substring(0, rating.start());
    String bound = rating.group(2) != null ? rating.group(2) : before.matches("(?s).*최대\\s*$") ? "최대" : null;
    ratings.add(new NumericConstraint(new BigDecimal(rating.group(3)), comparison(rating.group(5), bound)));
   }
  }
  LinkedHashSet<String> styles = new LinkedHashSet<>();
  LinkedHashSet<String> excludedStyles = new LinkedHashSet<>();
  List<StyleMention> styleMentions = new ArrayList<>();
  STYLE_KEYWORDS.forEach((style, keywords) -> {
   for (String keyword : keywords) {
    Matcher styleMatch = Pattern.compile(Pattern.quote(keyword)).matcher(text);
    while (styleMatch.find()) {
     if (isNegatedMention(text, styleMatch.end())) excludedStyles.add(style);
     else { styles.add(style); styleMentions.add(new StyleMention(styleMatch.start(), styleMatch.end(), style)); }
    }
   }
  });
  styles.removeAll(excludedStyles);
  styleMentions.removeIf(mention -> excludedStyles.contains(mention.style()));
  styleMentions.sort(Comparator.comparingInt(StyleMention::start));
  boolean stylesAny = false;
  for (int i = 1; i < styleMentions.size(); i++) {
   StyleMention previous = styleMentions.get(i - 1), current = styleMentions.get(i);
   if (!previous.style().equals(current.style()) && previous.end() <= current.start()
    && Pattern.compile("또는|혹은|이나|거나|나").matcher(text.substring(previous.end(), current.start())).find()) stylesAny = true;
  }
  return new ParsedQuery(count, scoreQuery, tokens, List.copyOf(careers), List.copyOf(ratings), Set.copyOf(styles), Set.copyOf(excludedStyles),
   stylesAny, containsAny(scoreQuery, List.of("경력", "베테랑", "숙련", "전문", "실력")),
   containsAny(scoreQuery, List.of("리뷰", "후기", "검증", "평점", "만족")), containsAny(scoreQuery, List.of("인기", "좋아요", "유명", "많이")), error);
 }

 private static boolean isRecommendationCount(String text, Matcher match) {
  String prefix = text.substring(0, match.start());
  String suffix = text.substring(match.end());
  // Accompanying people and elapsed time do not request additional recommendations.
  if (prefix.matches("(?s).*(?:친구|동료|가족|아이|자녀|고객|손님|우리|저희|일행|동행|남자|여자|성인)(?:와|과|랑|이랑)?\\s*$")) return false;
  if (suffix.matches("(?s)^\\s*(?:과|이랑|와|이서|이|가)?\\s*(?:함께|같이|동행).*")) return false;
  if ("분".equals(match.group(2))) {
   if (suffix.matches("(?s)^\\s*(?:후|전|동안|이내|이상|이하|소요|걸|만에|간|짜리).*")) return false;
   if (prefix.isBlank() && suffix.isBlank()) return true;
   return prefix.matches("(?s).*(?:디자이너|추천\\s*(?:인원|수)?|헤어디자이너)\\s*(?:는|를|은|을|이|가)?\\s*$")
    || suffix.matches("(?s)^\\s*(?:만|을|를|이|가|의)?\\s*(?:추천|찾아|디자이너|골라|보여).*" );
  }
  return true;
 }

 private static BigInteger parseCount(String expression) {
  expression = expression.replace(",", "");
  if (expression.matches("[+-]?\\d+")) return new BigInteger(expression);
  if (NATIVE_NUMBERS.containsKey(expression)) return BigInteger.valueOf(NATIVE_NUMBERS.get(expression));
  if (expression.contains(".")) return null;
  String digits = "영일이삼사오육칠팔구";
  BigInteger total = BigInteger.ZERO;
  BigInteger group = BigInteger.ZERO;
  int digit = 0;
  for (char character : expression.toCharArray()) {
   int index = digits.indexOf(character);
   if (index >= 0 || character == '공') { digit = Math.max(index, 0); continue; }
   long unit = switch (character) { case '십' -> 10; case '백' -> 100; case '천' -> 1000; case '만' -> 10000; case '억' -> 100000000; case '조' -> 1000000000000L; default -> 0; };
   if (unit == 0) return null;
   if (unit < 10000) group = group.add(BigInteger.valueOf((digit == 0 ? 1 : digit) * unit));
   else {
    group = group.add(BigInteger.valueOf(digit));
    total = total.add((group.signum() == 0 ? BigInteger.ONE : group).multiply(BigInteger.valueOf(unit)));
    group = BigInteger.ZERO;
   }
   digit = 0;
  }
  return total.add(group).add(BigInteger.valueOf(digit));
 }

 private static Map<String, Integer> nativeNumbers() {
  Map<String, Integer> values = new LinkedHashMap<>();
  values.put("영", 0); values.put("공", 0); values.put("제로", 0);
  String[] units = {"", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"};
  String[] tens = {"", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"};
  for (int ten = 0; ten < tens.length; ten++) for (int unit = 0; unit < units.length; unit++)
   if (ten + unit > 0) values.put(tens[ten] + units[unit], ten * 10 + unit);
  values.put("하나", 1); values.put("둘", 2); values.put("셋", 3); values.put("넷", 4); values.put("스무", 20);
  return values;
 }

 private static Comparison comparison(String suffix, String prefix) {
  if (suffix != null) return switch (suffix) { case "이하" -> Comparison.AT_MOST; case "초과" -> Comparison.GREATER; case "미만" -> Comparison.LESS; default -> Comparison.AT_LEAST; };
  return "최대".equals(prefix) ? Comparison.AT_MOST : Comparison.AT_LEAST;
 }

 public static String normalizeText(String value) {
  return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]+", " ").replaceAll("\\s+", " ").trim();
 }

 private static boolean containsAny(String text, List<String> words) { return words.stream().anyMatch(text::contains); }

 private static boolean isNegatedMention(String text, int end) {
  String suffix = text.substring(end, Math.min(text.length(), end + 18));
  return suffix.matches("(?s)^\\s*(?:은|는|을|를|이|가)?\\s*(?:말고|빼고|제외|아닌|싫|원하지|하지\\s*않).*" );
 }

 public static boolean hasPositiveStyleMention(String introduction, String style) {
  String text = normalizeText(introduction);
  for (String keyword : STYLE_KEYWORDS.getOrDefault(style, List.of())) {
   Matcher matcher = Pattern.compile(Pattern.quote(keyword)).matcher(text);
   while (matcher.find()) if (!isNegatedMention(text, matcher.end())) return true;
  }
  return false;
 }

 private record StyleMention(int start, int end, String style) {}

 public enum Comparison { AT_LEAST, AT_MOST, GREATER, LESS }
 public record NumericConstraint(BigDecimal threshold, Comparison comparison) {
  public boolean matches(BigDecimal value) {
   if (value == null) return false;
   int result = value.compareTo(threshold);
   return switch (comparison) { case AT_LEAST -> result >= 0; case AT_MOST -> result <= 0; case GREATER -> result > 0; case LESS -> result < 0; };
  }
 }
 public record ParsedQuery(BigInteger recommendationCount, String scoringQuery, List<String> tokens, List<NumericConstraint> careerConstraints,
                           List<NumericConstraint> ratingConstraints, Set<String> styles, Set<String> excludedStyles, boolean stylesAny,
                           boolean wantsExperienced, boolean wantsReviewProven, boolean wantsPopular, String validationError) {
  public int outputLimit(Integer legacyLimit, int eligibleCount) {
   if (recommendationCount != null) return recommendationCount.min(BigInteger.valueOf(eligibleCount)).intValueExact();
   return legacyLimit == null ? eligibleCount : Math.min(legacyLimit, eligibleCount);
  }
 }
}

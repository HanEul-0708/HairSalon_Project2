package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import static org.assertj.core.api.Assertions.assertThat;

class DesignerAiQueryParserTest {
 private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
 private static final Validator VALIDATOR = VALIDATION.getValidator();
 @AfterAll static void closeValidator() { VALIDATION.close(); }

 @ParameterizedTest
 @CsvSource({"디자이너 2명 추천,2", "디자이너 한 명 추천,1", "디자이너 두분 추천,2", "세 명 추천해줘,3", "네 명 추천,4", "디자이너 다섯 명,5", "디자이너 여섯 명,6", "디자이너 일곱명,7", "디자이너 열두명,12", "디자이너 스물한명,21", "디자이너 스무명,20", "디자이너 십오명,15", "디자이너 이십명,20", "디자이너 백명,100", "디자이너 ２명,2", "두 분,2", "디자이너 두 명 또는 2명 추천,2", "'디자이너 1,000명 추천',1000"})
 void parsesCommonDigitAndKoreanCounts(String text, String count) {
  var intent = DesignerAiQueryParser.parse(text);
  assertThat(intent.recommendationCount()).isEqualTo(new BigInteger(count));
  assertThat(intent.validationError()).isNull();
 }

 @ParameterizedTest
 @ValueSource(strings = {"친구 2명과 커트하러 갈 디자이너 추천", "친구와 두 명이 갈 디자이너 추천", "가족 세 명과 함께 갈 미용실", "성인 2명이 함께 받을 커트를 잘하는 디자이너 추천해줘", "경력 10년 이상 평점4.5점 디자이너 추천", "30분 이내에 커트할 디자이너", "30분 후 방문할 디자이너", "좋은 디자이너", ""})
 void doesNotMistakeCompanionsCareerRatingsOrTimeForRecommendationCount(String text) {
  assertThat(DesignerAiQueryParser.parse(text).recommendationCount()).isNull();
 }

 @Test
 void extractsRecommendationCountEvenWhenCompanionCountsArePresent() {
  assertThat(DesignerAiQueryParser.parse("친구2명과 커트하러 갈 디자이너 3명 추천").recommendationCount()).isEqualTo(BigInteger.valueOf(3));
 }

 @ParameterizedTest
 @ValueSource(strings = {"디자이너 0명", "디자이너 영명", "디자이너 -1명", "디자이너 1.5명", "디자이너 2명과 3명 추천", "디자이너 한명 또는 세명 추천"})
 void countErrorsAreBoundToTheQueryFieldBeforeServiceInvocation(String query) {
  var request = new DesignerAiRecommendationRequest();
  request.setQuery(query);
  assertThat(VALIDATOR.validate(request)).anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("query"));
 }

 @Test
 void optionalLegacyLimitAcceptsOmissionAndMoreThanSixButRejectsZero() {
  var request = new DesignerAiRecommendationRequest();
  request.setQuery("커트 디자이너");
  assertThat(request.getLimit()).isNull();
  assertThat(VALIDATOR.validate(request)).isEmpty();
  request.setLimit(100);
  assertThat(VALIDATOR.validate(request)).isEmpty();
  request.setLimit(0);
  assertThat(VALIDATOR.validate(request)).anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("limit"));
 }

 @Test
 void hugeNaturalCountIsSafeAndClampsOnlyAtActualEligibleCount() {
  var request = new DesignerAiRecommendationRequest();
  request.setQuery("디자이너 999999999999999999999999999999999999999999999999999명 추천");
  assertThat(VALIDATOR.validate(request)).isEmpty();
  var intent = DesignerAiQueryParser.parse(request.getQuery());
  assertThat(intent.outputLimit(null, 129)).isEqualTo(129);
  assertThat(intent.outputLimit(2, 129)).isEqualTo(129);
 }

 @Test
 void countClauseIsRemovedFromRankingTokensWithoutLosingCareerAndRatingConstraints() {
  var intent = DesignerAiQueryParser.parse("경력5년 이상 평점4.5점 이상 디자이너 2명 추천해줘");
  assertThat(intent.tokens()).doesNotContain("2명");
  assertThat(intent.careerConstraints()).hasSize(1);
  assertThat(intent.ratingConstraints()).hasSize(1);
 }

 @ParameterizedTest
 @ValueSource(strings = {"20년 만에 펌을 하려고", "10년 된 미용실", "결혼 5년 기념으로 스타일링"})
 void yearsWithoutCareerContextAreNotCareerConditions(String query) {
  assertThat(DesignerAiQueryParser.parse(query).careerConstraints()).isEmpty();
 }

 @Test
 void compoundCareerBoundsRetainTheirContext() {
  assertThat(DesignerAiQueryParser.parse("경력5년 이상 10년 미만").careerConstraints()).hasSize(2);
 }
}

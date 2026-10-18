package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.llm.DesignerAiLlmRecommendation;
import com.hairsalonproject2.designer.llm.DesignerAiLlmResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignerAiNaturalCountServiceTest {
 @Mock private DesignerAiCandidateService candidates;
 @Mock private DesignerAiClient client;
 private DesignerAiRecommendationService service;
 @BeforeEach void setUp() { service = new DesignerAiRecommendationService(candidates, client); }

 @ParameterizedTest
 @CsvSource({"디자이너 1명 추천해줘,1", "디자이너 두 명 추천해줘,2", "디자이너 7명 추천해줘,7", "디자이너 100명 추천해줘,12", "디자이너 99999999999999999999999999999999999999명 추천해줘,12", "경력 있는 디자이너,12"})
 void countComesFromQueryAndMissingCountReturnsEveryEligibleDesigner(String query, int count) {
  stubTwelveCandidates();
  assertThat(service.recommend(query, null).getRecommendations()).hasSize(count);
  verify(client).recommend(eq(query), argThat(list -> list.size() == 8), eq(Math.min(count, 8)));
 }

 @Test
 void naturalCountOverridesLegacyLimitWithoutChangingRanking() {
  stubTwelveCandidates();
  assertThat(ids(service.recommend("경력 있는 디자이너 2명 추천해줘", 10))).containsExactly(12, 11);
  assertThat(ids(service.recommend("경력 있는 디자이너", 2))).containsExactly(12, 11);
 }

 @Test
 void appendsEligibleRemainderAfterPartialLlmRejectsDuplicatesAndReportsActualCount() {
  stubTwelveCandidates();
  when(client.recommend(anyString(), anyList(), eq(8))).thenReturn(Optional.of(new DesignerAiLlmResult("99명을 추천했습니다.", List.of(
   new DesignerAiLlmRecommendation(9, "LLM 이유", List.of()), new DesignerAiLlmRecommendation(9, "중복", List.of()),
   new DesignerAiLlmRecommendation(1, "창 밖", List.of()), new DesignerAiLlmRecommendation(999, "후보 아님", List.of())))));
  var response = service.recommend("경력 있는 디자이너", null);
  assertThat(ids(response)).containsExactly(9, 12, 11, 10, 8, 7, 6, 5, 4, 3, 2, 1);
  assertThat(response.isLlmUsed()).isTrue();
  assertThat(response.getSummary()).contains("12명의").doesNotContain("99명");
  assertThat(response.getRecommendations().getLast().getReason()).doesNotContain("창 밖");
 }

 @Test
 void sharedSalonServicesNeverMakeAnotherSpecialtyEligibleOrFillOutsideConditions() {
  when(candidates.loadCandidates()).thenReturn(List.of(candidate(1, "CUT", "", 5, "4.5"), candidate(2, "COLOR", "", 10, "5.0")));
  when(client.recommend(anyString(), anyList(), eq(1))).thenReturn(Optional.of(new DesignerAiLlmResult("2명", List.of(new DesignerAiLlmRecommendation(2, "잘못된 추천", List.of())))));
  var response = service.recommend("커트 디자이너 10명 추천해줘", null);
  assertThat(ids(response)).containsExactly(1);
  assertThat(response.isLlmUsed()).isFalse();
  assertThat(response.getSummary()).contains("1명의");
 }

 @ParameterizedTest
 @CsvSource({"커트와 염색 전문,1", "커트 또는 염색 전문,3", "커트와 염색 잘하고 친절하거나 차분한 디자이너,1", "염색 말고 커트 전문,1", "뿌리 볼륨펌,1"})
 void combinesStyleConditionsWithAndOrAndExclusions(String query, int expectedCount) {
  when(candidates.loadCandidates()).thenReturn(List.of(candidate(1, "CUT", "염색도 전문", 5, "4.5"), candidate(2, "CUT", "커트 전문", 5, "4.5"), candidate(3, "PERM", "펌 전문", 5, "4.5"), candidate(4, "COLOR", "염색 전문", 5, "4.5")));
  assertThat(service.recommend(query, null).getRecommendations()).hasSize(expectedCount);
 }

 @ParameterizedTest
 @CsvSource({"평점4.5점 이상,4.5,true", "평점4.5점 초과,4.5,false", "평점4.5점 이하,4.5,true", "평점4.5점 미만,4.5,false", "평점4.5점 미만,4.4,true", "평점4.5점,4.4,false", "평점이 4.5 이상인 디자이너 2명,4.4,false", "최대 평점 4.5점인 디자이너,4.6,false", "최대 평점4.5점,4.4,true"})
 void ratingBoundsAreHardConditions(String query, String rating, boolean eligible) {
  when(candidates.loadCandidates()).thenReturn(List.of(candidate(1, "CUT", "", 5, rating)));
  assertThat(service.recommend(query, null).getRecommendations()).hasSize(eligible ? 1 : 0);
  if (!eligible) verifyNoInteractions(client);
 }

 @ParameterizedTest
 @CsvSource({"경력5년 이상,true", "경력5년 초과,false", "경력5년 이하,true", "경력5년 미만,false", "경력6년,false", "5년차,true", "5년 이상 경력,true", "20년 만에 커트,true", "10년 된 미용실,true", "최대 경력 4년인 디자이너,false", "최대 경력5년,true"})
 void careerBoundsAreHardConditionsOnlyWhenCareerIsDescribed(String query, boolean eligible) {
  when(candidates.loadCandidates()).thenReturn(List.of(candidate(1, "CUT", "", 5, "4.6")));
  assertThat(service.recommend(query, null).getRecommendations()).hasSize(eligible ? 1 : 0);
  if (!eligible) verifyNoInteractions(client);
 }

 @ParameterizedTest
 @ValueSource(strings = {"디자이너 0명 추천", "디자이너 -1명 추천", "디자이너 1.5명 추천", "디자이너 2명과 3명 추천"})
 void invalidCountsFailBeforeCandidateOrLlmCalls(String query) {
  assertThatIllegalArgumentException().isThrownBy(() -> service.recommend(query, null));
  verifyNoInteractions(candidates, client);
 }

 private void stubTwelveCandidates() {
  when(candidates.loadCandidates()).thenReturn(IntStream.rangeClosed(1, 12).mapToObj(id -> candidate(id, "CUT", "", id, "0")).toList());
 }
 private List<Integer> ids(DesignerAiRecommendationResponse response) { return response.getRecommendations().stream().map(item -> item.getDesignerId()).toList(); }
 private DesignerAiCandidatePrompt candidate(int id, String specialty, String intro, int career, String rating) {
  return new DesignerAiCandidatePrompt(id, 1, "Designer " + id, "Salon", specialty, career, new BigDecimal(rating), 0L, 0, intro,
   List.of("커트", "염색", "펌", "스타일링"), List.of());
 }
}

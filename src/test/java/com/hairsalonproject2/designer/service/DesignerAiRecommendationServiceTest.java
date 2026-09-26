package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.llm.DesignerAiLlmRecommendation;
import com.hairsalonproject2.designer.llm.DesignerAiLlmResult;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DesignerAiRecommendationServiceTest {
 @Mock
 private DesignerRepository designerRepository;

 @Mock
 private SalonServiceRepository salonServiceRepository;

 @Mock
 private ReviewRepository reviewRepository;

 @Mock
 private DesignerAiClient designerAiClient;

 private DesignerAiRecommendationService service;

 @BeforeEach
 void setUp() {
  service = new DesignerAiRecommendationService(new DesignerAiCandidateService(designerRepository, salonServiceRepository, reviewRepository), designerAiClient);
 }

 @Test
 void recommendsDesignerByStyleKeywordWithoutLlm() {
  Salon colorSalon = salon(1, "Color Lab");
  Salon cutSalon = salon(2, "Cut Studio");
  Designer colorDesigner = designer(1, colorSalon, "Color Kim", "톤다운 염색과 퍼스널 컬러 상담 전문", 8, DesignerSpecialty.COLOR, 24);
  Designer cutDesigner = designer(2, cutSalon, "Cut Lee", "남성 커트와 다운펌 전문", 5, DesignerSpecialty.CUT, 7);
  when(designerRepository.findAll()).thenReturn(List.of(cutDesigner, colorDesigner));
  when(designerRepository.findDesignerRatingRowsByIds(anyList())).thenReturn(List.of(ratingRow(1, 4.7, 18L), ratingRow(2, 4.9, 12L)));
  when(salonServiceRepository.findBySalonSalonIdInOrderByServiceIdAsc(anyList())).thenReturn(List.of(
   service(colorSalon, "프리미엄 염색", "톤다운, 톤업, 브릿지 컬러"), service(cutSalon, "남성 커트", "깔끔한 비즈니스 커트")));
  when(reviewRepository.findFirstThreeSnippetsByDesignerIds(anyList())).thenReturn(List.of());
  when(designerAiClient.recommend(anyString(), anyList(), eq(3))).thenReturn(Optional.empty());
  DesignerAiRecommendationResponse response = service.recommend("톤다운 염색 잘하는 디자이너", 3);
  assertThat(response.isLlmUsed()).isFalse();
  assertThat(response.getSource()).isEqualTo("LOCAL_RULES");
  assertThat(response.getRecommendations()).isNotEmpty();
  assertThat(response.getRecommendations().getFirst().getDesignerId()).isEqualTo(1);
  assertThat(response.getRecommendations().getFirst().getTags()).contains("염색");
 }

 @Test
 void appliesLlmOrderingAndReasonWhenAvailable() {
  Salon firstSalon = salon(1, "First Salon");
  Salon secondSalon = salon(2, "Second Salon");
  Designer firstDesigner = designer(1, firstSalon, "First Designer", "커트 전문", 6, DesignerSpecialty.CUT, 4);
  Designer secondDesigner = designer(2, secondSalon, "Second Designer", "스타일링과 드라이 전문", 4, DesignerSpecialty.STYLING, 15);
  when(designerRepository.findAll()).thenReturn(List.of(firstDesigner, secondDesigner));
  when(designerRepository.findDesignerRatingRowsByIds(anyList())).thenReturn(List.of(ratingRow(1, 4.8, 10L), ratingRow(2, 4.5, 8L)));
  when(salonServiceRepository.findBySalonSalonIdInOrderByServiceIdAsc(anyList())).thenReturn(List.of(
   service(firstSalon, "커트", "레이어드 커트"), service(secondSalon, "드라이", "행사용 스타일링")));
  when(reviewRepository.findFirstThreeSnippetsByDesignerIds(anyList())).thenReturn(List.of());
  when(designerAiClient.recommend(anyString(), anyList(), eq(2))).thenReturn(Optional.of(new DesignerAiLlmResult("LLM이 스타일링 요청에 맞춰 추천했습니다.", List.of(new DesignerAiLlmRecommendation(2, "행사용 스타일링 요청과 가장 잘 맞습니다.", List.of("스타일링", "행사"))))));
  DesignerAiRecommendationResponse response = service.recommend("행사 전에 드라이 잘하는 디자이너", 2);
  assertThat(response.isLlmUsed()).isTrue();
  assertThat(response.getSource()).isEqualTo("LLM");
  assertThat(response.getRecommendations()).hasSize(2);
  assertThat(response.getRecommendations().getFirst().getDesignerId()).isEqualTo(2);
  assertThat(response.getRecommendations().getFirst().getReason()).isEqualTo("행사용 스타일링 요청과 가장 잘 맞습니다.");
 }

 @ParameterizedTest
 @CsvSource({
  "평점 4.5점 이상, 4.6, 평점 5점 이상, 22.0",
  "평점 4.25점 이상, 4.3, 평점 5점 이상, 22.0",
  "평점 4.95점 이상, 4.97, 평점 5점 이상, 22.0",
  "평점 ４．５점 이상, 4.6, 평점 5점 이상, 22.0",
  "평점 4.1점 이상, 4.05, 평점 1점 이상, -22.0",
  "평점 4.9점 이상, 4.8, 평점 1점 이상, -22.0",
  "평점 4점 이상, 4.6, 평점 5점 이상, 22.0"
 })
 void appliesRatingPenaltyUsingOriginalDecimalThreshold(String query, double rating, String baselineQuery, double expectedDifference) {
  stubSingleCandidate(5, rating);

  double baselineScore = service.recommend(baselineQuery, 1).getRecommendations().getFirst().getScore();
  double actualScore = service.recommend(query, 1).getRecommendations().getFirst().getScore();

  assertThat(actualScore - baselineScore).isCloseTo(expectedDifference, org.assertj.core.data.Offset.offset(0.001));
 }

 @ParameterizedTest
 @CsvSource({"경력 5년 이상, 경력 6년 이상", "경력 ５년 이상, 경력 ６년 이상"})
 void continuesToApplyIntegerCareerThresholds(String query, String baselineQuery) {
  stubSingleCandidate(5, 4.6);

  double baselineScore = service.recommend(baselineQuery, 1).getRecommendations().getFirst().getScore();
  double actualScore = service.recommend(query, 1).getRecommendations().getFirst().getScore();

  assertThat(actualScore - baselineScore).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.001));
 }

 private void stubSingleCandidate(int careerYears, double rating) {
  Salon salon = salon(1, "Salon");
  Designer designer = designer(1, salon, "Designer", "", careerYears, DesignerSpecialty.CUT, 0);
  when(designerRepository.findAll()).thenReturn(List.of(designer));
  when(designerRepository.findDesignerRatingRowsByIds(anyList())).thenReturn(List.of(ratingRow(1, rating, 0L)));
  when(salonServiceRepository.findBySalonSalonIdInOrderByServiceIdAsc(anyList())).thenReturn(List.of());
  when(reviewRepository.findFirstThreeSnippetsByDesignerIds(anyList())).thenReturn(List.of());
  when(designerAiClient.recommend(anyString(), anyList(), eq(1))).thenReturn(Optional.empty());
 }

 @Test
 void scoresEveryCandidateBeforeSendingTopEightAndCapsRecommendationsAtSix() {
  Salon salon = salon(1, "Salon");
  var designers = java.util.stream.IntStream.rangeClosed(1, 12)
   .mapToObj(id -> designer(id, salon, "Designer " + id, "", id, DesignerSpecialty.CUT, 0)).toList();
  when(designerRepository.findAll()).thenReturn(designers);
  when(designerRepository.findDesignerRatingRowsByIds(anyList())).thenReturn(List.of());
  when(salonServiceRepository.findBySalonSalonIdInOrderByServiceIdAsc(anyList())).thenReturn(List.of());
  when(reviewRepository.findFirstThreeSnippetsByDesignerIds(anyList())).thenReturn(List.of());
  when(designerAiClient.recommend(eq("경력 있는 디자이너"), anyList(), eq(6))).thenAnswer(invocation -> {
   List<com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt> candidates = invocation.getArgument(1);
   assertThat(candidates).extracting(com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt::designerId)
    .containsExactly(12, 11, 10, 9, 8, 7, 6, 5);
   return Optional.empty();
  });

  var response = service.recommend("경력 있는 디자이너", 100);

  assertThat(response.getRecommendations()).extracting(item -> item.getDesignerId()).containsExactly(12, 11, 10, 9, 8, 7);
  verify(designerAiClient).recommend(eq("경력 있는 디자이너"), anyList(), eq(6));
  verify(designerRepository).findAll();
 }

 @Test
 void blankQueryDoesNotReadCandidatesOrCallLlm() {
  assertThat(service.recommend("  ", 3).getRecommendations()).isEmpty();
  verifyNoInteractions(designerRepository, salonServiceRepository, reviewRepository, designerAiClient);
 }

 private Salon salon(Integer salonId, String name) {
  return Salon.builder().salonId(salonId).name(name).address("Seoul").reservable(true).build();
 }

 private Designer designer(Integer designerId, Salon salon, String name, String introduction, Integer careerYears, DesignerSpecialty specialty, Integer likeCount) {
  return Designer.builder().designerId(designerId).salon(salon).name(name).introduction(introduction).careerYears(careerYears).specialty(specialty).likeCount(likeCount).build();
 }

 private SalonService service(Salon salon, String name, String description) {
  return SalonService.builder().salon(salon).name(name).description(description).price(50000).duration(60).build();
 }

 private DesignerRatingRow ratingRow(Integer designerId, Double averageRating, Long reviewCount) {
  return new DesignerRatingRow() {
   @Override
   public Integer getDesignerId() {
	return designerId;
   }

   @Override
   public Double getAverageRating() {
	return averageRating;
   }

   @Override
   public Long getReviewCount() {
	return reviewCount;
   }
  };
 }
}

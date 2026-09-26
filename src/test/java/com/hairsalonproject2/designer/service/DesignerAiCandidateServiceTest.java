package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.projection.DesignerReviewSnippetRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignerAiCandidateServiceTest {
 @Mock private DesignerRepository designers;
 @Mock private SalonServiceRepository services;
 @Mock private ReviewRepository reviews;

 @Test
 void batchesDistinctSalonsAndKeepsFirstTwelveDistinctServiceTexts() {
  Salon salon = Salon.builder().salonId(1).name("살롱").build();
  Salon otherSalon = Salon.builder().salonId(2).name("다른 살롱").build();
  when(designers.findAll()).thenReturn(List.of(designer(1, salon), designer(2, salon), designer(3, otherSalon)));
  when(designers.findDesignerRatingRowsByIds(List.of(1, 2, 3))).thenReturn(List.of());
  List<SalonService> rows = new ArrayList<>();
  for (int i = 1; i <= 14; i++) {
   rows.add(service(i, salon, "시술" + i));
   rows.add(service(i + 100, salon, "시술" + i));
  }
  rows.add(service(200, otherSalon, "염색"));
  when(services.findBySalonSalonIdInOrderByServiceIdAsc(List.of(1, 2))).thenReturn(rows);
  when(reviews.findFirstThreeSnippetsByDesignerIds(List.of(1, 2, 3)))
   .thenReturn(List.of(snippet(1, " 첫째\n\t리뷰 "), snippet(1, "둘째"), snippet(1, "가".repeat(100)), snippet(3, "다른 리뷰")));

  var result = new DesignerAiCandidateService(designers, services, reviews).loadCandidates();

  assertThat(result).hasSize(3);
  assertThat(result.get(0).services()).containsExactlyElementsOf(
   java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "시술" + i).toList());
  assertThat(result.get(1).services()).isEqualTo(result.get(0).services());
  assertThat(result.get(2).services()).containsExactly("염색");
  assertThat(result.get(0).reviewSnippets()).containsExactly("첫째 리뷰", "둘째", "가".repeat(90));
  assertThat(result.get(1).reviewSnippets()).isEmpty();
  assertThat(result.get(2).reviewSnippets()).containsExactly("다른 리뷰");
  verify(designers).findAll();
  verify(designers).findDesignerRatingRowsByIds(List.of(1, 2, 3));
  verify(services).findBySalonSalonIdInOrderByServiceIdAsc(List.of(1, 2));
  verify(reviews).findFirstThreeSnippetsByDesignerIds(List.of(1, 2, 3));
  verifyNoMoreInteractions(designers, services, reviews);
 }

 @Test
 void emptyCatalogAvoidsEmptyInQueries() {
  when(designers.findAll()).thenReturn(List.of());
  assertThat(new DesignerAiCandidateService(designers, services, reviews).loadCandidates()).isEmpty();
  verify(designers).findAll();
  verifyNoMoreInteractions(designers);
  verifyNoInteractions(services, reviews);
 }

 private Designer designer(int id, Salon salon) {
  return Designer.builder().designerId(id).salon(salon).name("디자이너" + id).build();
 }
 private SalonService service(int id, Salon salon, String name) {
  return SalonService.builder().serviceId(id).salon(salon).name(name).build();
 }
 private DesignerReviewSnippetRow snippet(int id, String content) {
  return new DesignerReviewSnippetRow() {
   public Integer getDesignerId() { return id; }
   public String getContent() { return content; }
  };
 }
}

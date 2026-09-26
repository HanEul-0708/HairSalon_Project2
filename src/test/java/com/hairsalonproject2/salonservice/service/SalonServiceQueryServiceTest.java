package com.hairsalonproject2.salonservice.service;

import com.hairsalonproject2.common.catalog.CatalogConflictException;
import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.projection.ServicePriceCompareRow;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalonServiceQueryServiceTest {
 @Mock private SalonServiceRepository salonServiceRepository;
 @Mock private SalonRepository salonRepository;
 @Mock private CatalogIntegrityService catalogIntegrityService;
 @InjectMocks private SalonServiceQueryService queryService;

 @ParameterizedTest
 @ValueSource(strings = {"rating", "trend"})
 void fractionalRatingsTakePrecedenceOverPrice(String sortBy) {
  SalonService cheaper = service(1, "커트", 10000, "4.1");
  SalonService higherRated = service(2, "커트", 40000, "4.9");
  if ("trend".equals(sortBy)) {
   when(salonServiceRepository.searchServices(null, null, null, null, null)).thenReturn(List.of(cheaper, higherRated));
  } else {
   when(salonServiceRepository.searchServices(null, null, null, null, null, "rating", PageRequest.of(0, 15)))
           .thenReturn(new PageImpl<>(List.of(higherRated, cheaper)));
  }
  SalonServiceSearchRequest request = new SalonServiceSearchRequest();
  request.setSortBy(sortBy);

  List<SalonServiceSummaryResponse> result = queryService.list(request, 0, 15).getContent();

  assertThat(result).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(2, 1);
  assertThat(result.get(0).getAverageRating()).isEqualByComparingTo("4.9");
  assertThat(result.get(1).getAverageRating()).isEqualByComparingTo("4.1");
 }

 @Test
 void equalRatingsStillSortByPriceThenNameAndMissingRatingsBecomeZero() {
  when(salonServiceRepository.searchServices(null, null, null, null, null)).thenReturn(List.of(
          service(1, "C", 30000, "4.90"),
          service(2, "B", 20000, "4.9"),
          service(3, "A", 20000, "4.90"),
          service(4, "D", 0, null)));
  SalonServiceSearchRequest request = new SalonServiceSearchRequest();
  request.setSortBy("rating");

  List<SalonServiceSummaryResponse> result = queryService.list(request);

  assertThat(result).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(3, 2, 1, 4);
  assertThat(result.get(3).getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
 }

 @Test
 void comparisonsPreserveFractionsAndNormalizeMissingRatingToZero() {
  ServicePriceCompareRow rated = mock(ServicePriceCompareRow.class);
  ServicePriceCompareRow unrated = mock(ServicePriceCompareRow.class);
  when(rated.getAverageRating()).thenReturn(new BigDecimal("4.91"));
  when(salonServiceRepository.compareServices("커트", "서울")).thenReturn(List.of(rated, unrated));

  var comparisons = queryService.compare("커트", "서울");

  assertThat(comparisons.get(0).getAverageRating()).isEqualByComparingTo("4.91");
  assertThat(comparisons.get(1).getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
 }

 @Test
 void salonExistenceCheckRejectsInvalidIdsWithoutRepositoryCalls() {
  assertThat(queryService.existsSalon(null)).isFalse();
  assertThat(queryService.existsSalon(0)).isFalse();
  assertThat(queryService.existsSalon(-1)).isFalse();
  verifyNoInteractions(salonRepository);

  when(salonRepository.existsById(7)).thenReturn(true);
  assertThat(queryService.existsSalon(7)).isTrue();
  assertThat(queryService.existsSalon(99)).isFalse();
 }

 @Test
 void createStillRejectsSalonRemovedAfterFormValidation() {
  SalonServiceCreateRequest request = new SalonServiceCreateRequest();
  request.setSalonId(99);
  request.setName("커트");
  request.setPrice(10000);
  request.setDuration(30);
  when(salonRepository.findById(99)).thenReturn(Optional.empty());

  assertThatThrownBy(() -> queryService.create(request)).isInstanceOf(EntityNotFoundException.class);

  verifyNoInteractions(salonServiceRepository);
 }

 @Test
 void updateDoesNotMutateServiceWhenReferencedSalonIsMissing() {
  SalonService existing = service(1, "기존 시술", 10000, "4.5");
  Salon originalSalon = existing.getSalon();
  SalonServiceUpdateRequest request = new SalonServiceUpdateRequest();
  request.setSalonId(99);
  request.setName("수정 시술");
  request.setPrice(20000);
  request.setDuration(60);
  when(salonServiceRepository.findById(1)).thenReturn(Optional.of(existing));
  when(salonRepository.findById(99)).thenReturn(Optional.empty());

  assertThatThrownBy(() -> queryService.update(1, request)).isInstanceOf(EntityNotFoundException.class);

  assertThat(existing.getSalon()).isSameAs(originalSalon);
  assertThat(existing.getName()).isEqualTo("기존 시술");
  assertThat(existing.getPrice()).isEqualTo(10000);
  assertThat(existing.getDuration()).isEqualTo(30);
  verify(salonServiceRepository, never()).save(any());
 }

 @Test
 void databasePagingTrimsFiltersAndClampsHugePageWithoutOverflow() {
  SalonServiceSearchRequest request = new SalonServiceSearchRequest();
  request.setKeyword(" 커트 ");
  request.setSalonKeyword(" 살롱 ");
  request.setRegion(" 서울 ");
  request.setMaxPrice(30000);
  request.setMaxDuration(60);
  request.setSortBy("rating");
  PageRequest boundedPage = PageRequest.of(Integer.MAX_VALUE / 15, 15);
  PageRequest lastPage = PageRequest.of(1, 15);
  when(salonServiceRepository.searchServices("커트", "살롱", "서울", 30000, 60, "rating", boundedPage))
          .thenReturn(new PageImpl<>(List.of(), boundedPage, 16));
  when(salonServiceRepository.searchServices("커트", "살롱", "서울", 30000, 60, "rating", lastPage))
          .thenReturn(new PageImpl<>(List.of(service(16, "커트", 25000, "4.91")), lastPage, 16));

  var result = queryService.list(request, Integer.MAX_VALUE, 15);

  assertThat(result.getNumber()).isEqualTo(1);
  assertThat(result.getTotalElements()).isEqualTo(16);
  assertThat(result.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(16);
  verify(salonServiceRepository, never()).searchServices(any(), any(), any(), any(), any());
 }

 @Test
 void comparisonPagesPreserveRoadAddressAndNormalizeBlankFilters() {
  ServicePriceCompareRow row = mock(ServicePriceCompareRow.class);
  when(row.getAddress()).thenReturn("서울 기존 주소");
  when(row.getRoadAddress()).thenReturn("서울 도로명 주소");
  when(salonServiceRepository.compareServices("커트", null, PageRequest.of(0, 15)))
          .thenReturn(new PageImpl<>(List.of(row)));

  var page = queryService.compare(" 커트 ", "  ", 0, 15);

  assertThat(page.getContent().get(0).getRoadAddress()).isEqualTo("서울 도로명 주소");
  assertThat(page.getContent().get(0).getAddress()).isEqualTo("서울 기존 주소");
  assertThat(page.getContent().get(0).getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
 }

 @Test
 void trendUsesNormalizedFrequencyThenStableIdAndClampsHugePage() {
  when(salonServiceRepository.searchServices(null, null, null, null, null)).thenReturn(List.of(
          service(3, "커트", 20000, "4.0"), service(2, "커트", 20000, "4.0"),
          service(1, "펌", 10000, "5.0")));
  SalonServiceSearchRequest request = new SalonServiceSearchRequest();
  request.setSortBy("trend");

  var first = queryService.list(request, 0, 2);
  var last = queryService.list(request, Integer.MAX_VALUE, 2);

  assertThat(first.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(2, 3);
  assertThat(last.getNumber()).isEqualTo(1);
  assertThat(last.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(1);
 }

 @Test
 void conflictingMoveStopsBeforeAnyMutation() {
  doThrow(new CatalogConflictException("salonId", "예약 이력이 있는 시술입니다."))
          .when(catalogIntegrityService).assertServiceMoveAllowed(1, 2);
  SalonServiceUpdateRequest request = new SalonServiceUpdateRequest();
  request.setSalonId(2);
  assertThatThrownBy(() -> queryService.update(1, request)).isInstanceOf(CatalogConflictException.class);
  verifyNoInteractions(salonServiceRepository, salonRepository);
 }

 @Test
 void conflictingDeleteNeverInvokesRepositoryDelete() {
  doThrow(new CatalogConflictException(null, "예약 이력이 있는 시술입니다."))
          .when(catalogIntegrityService).assertServiceDeletable(1);
  assertThatThrownBy(() -> queryService.delete(1)).isInstanceOf(CatalogConflictException.class);
  verifyNoInteractions(salonServiceRepository);
 }

 private SalonService service(int id, String name, int price, String rating) {
  Salon salon = Salon.builder().salonId(id).name("살롱 " + id).address("서울")
          .averageRating(rating == null ? null : new BigDecimal(rating)).build();
  return SalonService.builder().serviceId(id).salon(salon).name(name).price(price).duration(30).build();
 }
}

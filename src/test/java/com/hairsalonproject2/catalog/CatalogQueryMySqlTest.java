package com.hairsalonproject2.catalog;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.service.DesignerAiCandidateService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogQueryMySqlTest extends CatalogMySqlTestSupport {
 @Autowired SalonQueryService salons;
 @Autowired SalonServiceQueryService services;
 @Autowired DesignerQueryService designers;
 @Autowired DesignerAiCandidateService candidates;

 @Test
 void salonDatabasePagesUseStableIdsAndTrimmedRoadAddressFilters() {
  List<Integer> ids = tx(em -> {
   List<Integer> result = new ArrayList<>();
   for (int i = 0; i < 31; i++) result.add(salon("Same salon", "Old address", "Seoul road", "4.50").getSalonId());
   salon("Excluded", "Elsewhere", "Busan road", "5.00");
   return result;
  });
  var request = new SalonSearchRequest();
  request.setRegion("  Seoul  ");
  request.setMinRating(new BigDecimal("4.50"));
  statistics().clear();
  var first = salons.search(request, 0, 15);
  assertThat(first.getTotalElements()).isEqualTo(31);
  assertThat(first.getContent()).extracting(SalonSummaryResponse::getSalonId).containsExactlyElementsOf(ids.subList(0, 15));
  assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
  assertThat(statistics().getEntityLoadCount()).isEqualTo(15);
  var last = salons.search(request, Integer.MAX_VALUE, 15);
  assertThat(last.getNumber()).isEqualTo(2);
  assertThat(last.getContent()).extracting(SalonSummaryResponse::getSalonId).containsExactly(ids.get(30));
 }

 @ParameterizedTest
 @CsvSource({"price, 0, 4, 3, 1, 2", "duration, 3, 0, 4, 2, 1", "rating, 3, 2, 0, 4, 1", "name, 0, 4, 3, 2, 1"})
 void serviceSortingRunsInDatabaseWithStableTiesAndNoSalonNPlusOne(String sort, int a, int b, int c, int d, int e) {
  List<Integer> ids = tx(em -> {
   var low = salon("Catalog", "Old address", "Seoul road", "3.00");
   var high = salon("Catalog", "Old address", "Seoul road", "5.00");
   return List.of(service(low, "Alpha", 10000, 40).getServiceId(),
     service(low, "Zulu", 20000, 60).getServiceId(),
     service(high, "Beta", 30000, 40).getServiceId(),
     service(high, "Beta", 20000, 20).getServiceId(),
     service(low, "Alpha", 10000, 40).getServiceId());
  });
  var request = new SalonServiceSearchRequest();
  request.setSortBy(" " + sort.toUpperCase() + " ");
  request.setSalonKeyword(" Catalog ");
  request.setRegion(" Seoul ");
  request.setMaxPrice(30000);
  request.setMaxDuration(60);
  statistics().clear();
  var first = services.list(request, 0, 2);
  assertThat(first.getTotalElements()).isEqualTo(5);
  assertThat(first.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(ids.get(a), ids.get(b));
  assertThat(first.getContent()).allSatisfy(row -> assertThat(row.getSalonName()).isEqualTo("Catalog"));
  assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
  assertThat(statistics().getEntityLoadCount()).isLessThanOrEqualTo(4);
  var second = services.list(request, 1, 2);
  assertThat(second.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(ids.get(c), ids.get(d));
  var last = services.list(request, Integer.MAX_VALUE, 2);
  assertThat(last.getNumber()).isEqualTo(2);
  assertThat(last.getContent()).extracting(SalonServiceSummaryResponse::getServiceId).containsExactly(ids.get(e));
 }

 @Test
 void compareUsesRoadAddressAndProjectionPagesWithoutLoadingEntities() {
  List<Integer> ids = tx(em -> {
   var salon = salon("Compare salon", "Legacy location", "Seoul road", "4.00");
   return IntStream.range(0, 4).mapToObj(i -> service(salon, "Cut", 20000, 30).getServiceId()).toList();
  });
  statistics().clear();
  var first = services.compare(" cut ", " Seoul ", 0, 2);
  assertThat(first.getTotalElements()).isEqualTo(4);
  assertThat(first.getContent()).extracting(row -> row.getServiceId()).containsExactly(ids.get(0), ids.get(1));
  assertThat(first.getContent()).allSatisfy(row -> assertThat(row.getRoadAddress()).isEqualTo("Seoul road"));
  assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
  assertThat(statistics().getEntityLoadCount()).isZero();
  assertThat(services.compare(" cut ", " Seoul ", Integer.MAX_VALUE, 2).getContent())
    .extracting(row -> row.getServiceId()).containsExactly(ids.get(2), ids.get(3));
 }

 @Test
 void allThreeDesignerListsShareOneCatalogSnapshotAndOneRatingQuery() {
  List<Integer> ids = tx(em -> {
   List<Integer> result = new ArrayList<>();
   for (int i = 0; i < 20; i++) {
    var salon = salon("Catalog " + i, "Address", "Road", "4.00");
    result.add(designer(salon, "Same designer").getDesignerId());
   }
   return result;
  });
  // A shared timestamp makes the final ID tie-breaker observable for the newest list too.
  jdbc.update("update designer set created_at = '2026-01-01 10:00:00'");
  var request = new DesignerSearchRequest();
  request.setSalonKeyword(" Catalog ");
  statistics().clear();
  var page = designers.searchCatalog(request, 0, 15);
  assertThat(page.rating().getTotalElements()).isEqualTo(20);
  assertThat(page.rating().getContent()).extracting(DesignerSummaryResponse::getDesignerId).containsExactlyElementsOf(ids.subList(0, 15));
  assertThat(page.likes().getContent()).extracting(DesignerSummaryResponse::getDesignerId).containsExactlyElementsOf(ids.subList(0, 15));
  assertThat(page.newest().getContent()).extracting(DesignerSummaryResponse::getDesignerId).containsExactlyElementsOf(ids.subList(0, 15));
  assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
  var last = designers.searchCatalog(request, Integer.MAX_VALUE, 15);
  for (var sortedPage : List.of(last.rating(), last.likes(), last.newest())) {
   assertThat(sortedPage.getNumber()).isEqualTo(1);
   assertThat(sortedPage.getContent()).extracting(DesignerSummaryResponse::getDesignerId).containsExactlyElementsOf(ids.subList(15, 20));
  }
 }

 @Test
 void aiLoadsAllCandidatesWithFourQueriesAndPerDesignerFirstThreeNonblankReviews() {
  List<Integer> ids = tx(em -> {
   var customer = member("ai-customer", MemberRole.USER);
   var salon = salon("AI salon", "Address", "Road", "4.00");
   var firstService = service(salon, "Service 0", 20000, 30);
   service(salon, "Service 0", 21000, 40); // Deduplication must happen before applying the limit.
   for (int i = 1; i < 14; i++) service(salon, "Service " + i, 20000, 30);
   List<Integer> result = new ArrayList<>();
   for (int i = 0; i < 10; i++) {
    Designer designer = designer(salon, "Designer " + i);
    result.add(designer.getDesignerId());
    for (String content : new String[]{null, "  ", "\t\n", " First  review ", "Second\nreview", "Third review", "Fourth excluded"}) {
     review(reservation(customer, designer, firstService, ReservationStatus.COMPLETED), designer, 5, content);
    }
   }
   return result;
  });
  statistics().clear();
  var results = candidates.loadCandidates();
  assertThat(results).hasSize(10);
  assertThat(results).extracting(row -> row.designerId()).containsExactlyInAnyOrderElementsOf(ids);
  assertThat(results).allSatisfy(row -> {
   assertThat(row.services()).containsExactlyElementsOf(IntStream.range(0, 12).mapToObj(i -> "Service " + i).toList());
   assertThat(row.reviewSnippets()).containsExactly("First review", "Second review", "Third review");
   assertThat(row.reviewCount()).isEqualTo(7);
  });
  assertThat(statistics().getPrepareStatementCount()).isEqualTo(4);
 }
}

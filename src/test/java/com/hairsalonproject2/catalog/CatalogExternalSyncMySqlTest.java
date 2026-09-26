package com.hairsalonproject2.catalog;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses real database transactions and catalog writers; only the network boundaries are mocked. */
class CatalogExternalSyncMySqlTest extends CatalogMySqlTestSupport {
 @Autowired private ExternalSalonSyncService sync;
 @Autowired private DesignerAiRecommendationService recommendations;
 @MockitoBean private KakaoLocalSearchClient kakao;
 @MockitoBean private DesignerAiClient aiClient;

 @Test
 void concurrentSyncOfTheSameExternalIdCreatesOneSalonAndOneSetOfSeeds() throws Exception {
  tx(em -> member("available-designer", MemberRole.DESIGNER));
  CountDownLatch bothCallsReachedNetwork = new CountDownLatch(2);
  when(kakao.searchSalons("hair", "Seoul", 1, 15)).thenAnswer(invocation -> {
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   bothCallsReachedNetwork.countDown();
   await(bothCallsReachedNetwork);
   return List.of(place("same-place"));
  });

  var results = concurrently(() -> sync.syncFromKakao("hair", "Seoul"),
    () -> sync.syncFromKakao("hair", "Seoul"));

  assertThat(results.getFirst()).hasSize(1).isEqualTo(results.getLast());
  int salonId = results.getFirst().getFirst();
  assertThat(count("salon")).isOne();
  assertThat(jdbc.queryForObject("select reservable from salon where salon_id = ?", Boolean.class, salonId)).isFalse();
  assertThat(count("designer")).isEqualTo(3);
  assertThat(count("salon_service")).isEqualTo(5);
  assertThat(jdbc.queryForObject("select count(*) from designer where member_id = 'available-designer'", Integer.class)).isOne();
  verify(kakao, times(2)).searchSalons("hair", "Seoul", 1, 15);
 }

 @Test
 void repeatSyncPreservesAdminReservabilityAndExistingSeedEdits() {
  when(kakao.searchSalons("hair", "Seoul", 1, 15)).thenReturn(List.of(place("existing-place")));
  int salonId = sync.syncFromKakao("hair", "Seoul").getFirst();
  int serviceId = jdbc.queryForObject("select min(service_id) from salon_service where salon_id = ?", Integer.class, salonId);
  jdbc.update("update salon set reservable = true where salon_id = ?", salonId);
  jdbc.update("update salon_service set name = ?, price = ? where service_id = ?", "관리자 지정 시술", 12345, serviceId);
  when(kakao.searchSalons("hair", "Seoul", 1, 15)).thenReturn(List.of(
    place("existing-place").toBuilder().placeName("갱신된 외부 미용실").phone("02-999-1234").build()));

  assertThat(sync.syncFromKakao("hair", "Seoul")).containsExactly(salonId);

  assertThat(count("salon")).isOne();
  assertThat(count("designer")).isEqualTo(3);
  assertThat(count("salon_service")).isEqualTo(5);
  assertThat(jdbc.queryForObject("select reservable from salon where salon_id = ?", Boolean.class, salonId)).isTrue();
  assertThat(jdbc.queryForObject("select name from salon where salon_id = ?", String.class, salonId)).isEqualTo("갱신된 외부 미용실");
  assertThat(jdbc.queryForObject("select phone from salon where salon_id = ?", String.class, salonId)).isEqualTo("02-999-1234");
  assertThat(jdbc.queryForObject("select name from salon_service where service_id = ?", String.class, serviceId)).isEqualTo("관리자 지정 시술");
  assertThat(jdbc.queryForObject("select price from salon_service where service_id = ?", Integer.class, serviceId)).isEqualTo(12345);
 }

 @Test
 void concurrentDifferentSalonsCannotAttachTheSameMemberTwice() throws Exception {
  tx(em -> member("only-designer-account", MemberRole.DESIGNER));
  CountDownLatch bothCallsReachedNetwork = new CountDownLatch(2);
  when(kakao.searchSalons(anyString(), eq("Seoul"), eq(1), eq(15))).thenAnswer(invocation -> {
   bothCallsReachedNetwork.countDown();
   await(bothCallsReachedNetwork);
   return List.of(place(invocation.getArgument(0, String.class)));
  });

  var results = concurrently(() -> sync.syncFromKakao("first-place", "Seoul"),
    () -> sync.syncFromKakao("second-place", "Seoul"));

  assertThat(results.getFirst()).hasSize(1);
  assertThat(results.getLast()).hasSize(1).doesNotContain(results.getFirst().getFirst());
  assertThat(count("salon")).isEqualTo(2);
  assertThat(count("designer")).isEqualTo(6);
  assertThat(count("salon_service")).isEqualTo(10);
  assertThat(jdbc.queryForObject("select count(*) from designer where member_id = 'only-designer-account'", Integer.class)).isOne();
  assertThat(jdbc.queryForObject("select count(*) from designer where member_id is not null", Integer.class)).isOne();
  verify(kakao, times(2)).searchSalons(anyString(), eq("Seoul"), eq(1), eq(15));
 }

 @Test
 void kakaoNetworkCallSuspendsTheCallersTransactionAndWriterCommitsIndependently() {
  when(kakao.searchSalons("hair", "Seoul", 1, 15)).thenAnswer(invocation -> {
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   return List.of(place("outside-caller-transaction"));
  });

  List<Integer> savedIds = new TransactionTemplate(transactionManager).execute(status -> {
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
   List<Integer> ids = sync.syncFromKakao("hair", "Seoul");
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
   status.setRollbackOnly();
   return ids;
  });

  assertThat(savedIds).hasSize(1);
  assertThat(count("salon")).isOne();
  verify(kakao).searchSalons("hair", "Seoul", 1, 15);
 }

 @Test
 void aiNetworkCallHappensAfterCandidateTransactionEndsAndSuspendsCallerTransaction() {
  Fixture fixture = fixture();
  when(aiClient.recommend(eq("커트"), anyList(), eq(3))).thenAnswer(invocation -> {
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   List<DesignerAiCandidatePrompt> candidates = invocation.getArgument(1);
   assertThat(candidates).extracting(DesignerAiCandidatePrompt::designerId).containsExactly(fixture.designerId());
   assertThat(candidates.getFirst().services()).containsExactly("Cut");
   return Optional.empty();
  });

  var result = tx(em -> {
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
   var response = recommendations.recommend("커트", 3);
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
   return response;
  });

  assertThat(result.getSource()).isEqualTo("LOCAL_RULES");
  assertThat(result.getRecommendations()).extracting(item -> item.getDesignerId()).containsExactly(fixture.designerId());
  verify(aiClient).recommend(eq("커트"), anyList(), eq(3));
  verifyNoInteractions(kakao);
 }

 private int count(String table) {
  return jdbc.queryForObject("select count(*) from " + table, Integer.class);
 }

 private static KakaoPlaceSearchResult place(String id) {
  return KakaoPlaceSearchResult.builder().externalId(id).placeName("외부 미용실 " + id)
    .addressName("서울 테스트 주소").roadAddressName("서울 테스트 도로명").phone("02-123-4567").build();
 }

 private static List<List<Integer>> concurrently(Callable<List<Integer>> first, Callable<List<Integer>> second) throws Exception {
  var workers = Executors.newFixedThreadPool(2);
  try {
   var firstResult = workers.submit(first);
   var secondResult = workers.submit(second);
   return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
  } finally {
   workers.shutdownNow();
   assertThat(workers.awaitTermination(20, TimeUnit.SECONDS)).as("all sync transactions must finish before database reset").isTrue();
  }
 }

 private static void await(CountDownLatch latch) {
  try {
   if (!latch.await(8, TimeUnit.SECONDS)) throw new AssertionError("Both sync calls must reach the network barrier");
  } catch (InterruptedException exception) {
   Thread.currentThread().interrupt();
   throw new AssertionError(exception);
  }
 }
}

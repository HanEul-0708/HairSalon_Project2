package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExternalSalonSyncServiceTest {
 @ParameterizedTest
 @MethodSource("retryableConflicts")
 void retriesOnlyDatabaseWriteWithoutRepeatingHttp(RuntimeException conflict) {
  KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
  ExternalSalonWriter writer = mock(ExternalSalonWriter.class);
  List<KakaoPlaceSearchResult> places = List.of(KakaoPlaceSearchResult.builder().externalId("1").build());
  when(client.searchSalons("hair", "Seoul", 1, 15)).thenReturn(places);
  when(writer.saveKakaoPlaces(places)).thenThrow(conflict).thenReturn(List.of(7));

  assertThat(new ExternalSalonSyncService(client, writer).syncFromKakao("hair", "Seoul"))
    .containsExactly(7);
  verify(client, times(1)).searchSalons("hair", "Seoul", 1, 15);
  verify(writer, times(2)).saveKakaoPlaces(places);
 }

 static Stream<RuntimeException> retryableConflicts() {
  return Stream.of(new DuplicateKeyException("duplicate"),
    new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23000", 1062)),
    new PessimisticLockingFailureException("deadlock"),
    new ExternalSalonWriteConflictException("candidate changed"));
 }

 @Test
 void exhaustionStopsAfterThreeAttemptsAndReportsRecoverableConflict() {
  KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
  ExternalSalonWriter writer = mock(ExternalSalonWriter.class);
  when(writer.saveKakaoPlaces(anyList())).thenThrow(new DuplicateKeyException("duplicate"));

  assertThatThrownBy(() -> new ExternalSalonSyncService(client, writer).syncFromKakao("hair", "Seoul"))
    .isInstanceOf(ExternalSalonSyncConflictException.class)
    .hasMessageContaining("다시 동기화")
    .hasCauseInstanceOf(DuplicateKeyException.class);
  verify(writer, times(3)).saveKakaoPlaces(anyList());
  verify(client, times(1)).searchSalons("hair", "Seoul", 1, 15);
 }

 @Test
 void nonDuplicateConstraintFailureIsNotRetriedOrMisreportedAsConcurrency() {
  KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
  ExternalSalonWriter writer = mock(ExternalSalonWriter.class);
  var constraintFailure = new DataIntegrityViolationException("missing parent",
    new SQLException("missing parent", "23000", 1452));
  when(writer.saveKakaoPlaces(anyList())).thenThrow(constraintFailure);

  assertThatThrownBy(() -> new ExternalSalonSyncService(client, writer).syncFromKakao("hair", "Seoul"))
    .isSameAs(constraintFailure);
  verify(writer, times(1)).saveKakaoPlaces(anyList());
 }

 @Test
 void httpFailureDoesNotStartWriting() {
  KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
  ExternalSalonWriter writer = mock(ExternalSalonWriter.class);
  when(client.searchSalons("hair", "Seoul", 1, 15)).thenThrow(new ResourceAccessException("timeout"));

  assertThatThrownBy(() -> new ExternalSalonSyncService(client, writer).syncFromKakao("hair", "Seoul"))
          .isInstanceOf(ResourceAccessException.class);
  verifyNoInteractions(writer);
 }

 @Test
 void httpRunsWithoutTransactionAndWriterUsesTransactionEvenWithTransactionalCaller() {
  try (var context = new AnnotationConfigApplicationContext()) {
   TestTransactionManager manager = new TestTransactionManager();
   KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
   SalonRepository salons = mock(SalonRepository.class);
   KakaoPlaceSearchResult place = KakaoPlaceSearchResult.builder().externalId("place-1")
           .placeName("Hair").addressName("Seoul").build();
   Salon existing = Salon.builder().salonId(1).reservable(true).build();
   when(client.searchSalons("hair", "Seoul", 1, 15)).thenAnswer(invocation -> {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    return List.of(place);
   });
   when(salons.findBySourceTypeAndExternalId("KAKAO", "place-1")).thenAnswer(invocation -> {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    return Optional.of(existing);
   });
   when(salons.save(existing)).thenReturn(existing);
   context.register(TransactionConfiguration.class);
   context.registerBean("transactionManager", TestTransactionManager.class, () -> manager);
   context.registerBean(ExternalSalonWriter.class, () -> new ExternalSalonWriter(salons,
           mock(DesignerRepository.class), mock(SalonServiceRepository.class),
           mock(SalonSeedDataFactory.class), mock(MemberRepository.class), mock(DummyTimelineService.class),
           mock(CatalogIntegrityService.class)));
   context.registerBean(ExternalSalonSyncService.class, () -> new ExternalSalonSyncService(
           client, context.getBean(ExternalSalonWriter.class)));
   context.refresh();

   ExternalSalonSyncService service = context.getBean(ExternalSalonSyncService.class);
   assertThat(service.syncFromKakao("hair", "Seoul")).containsExactly(1);
   new TransactionTemplate(manager).executeWithoutResult(status -> {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    assertThat(service.syncFromKakao("hair", "Seoul")).containsExactly(1);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
   });
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   verify(salons, times(2)).save(existing);
  }
 }

 @Test
 void aFailedWriterRollsBackBeforeRetryStartsANewTransaction() {
  try (var context = new AnnotationConfigApplicationContext()) {
   TestTransactionManager manager = new TestTransactionManager();
   KakaoLocalSearchClient client = mock(KakaoLocalSearchClient.class);
   SalonRepository salons = mock(SalonRepository.class);
   var place = KakaoPlaceSearchResult.builder().externalId("place-1").placeName("Hair").build();
   Salon existing = Salon.builder().salonId(1).reservable(true).build();
   when(client.searchSalons("hair", "Seoul", 1, 15)).thenAnswer(invocation -> {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    return List.of(place);
   });
   when(salons.findBySourceTypeAndExternalId("KAKAO", "place-1")).thenReturn(Optional.of(existing));
   when(salons.save(existing)).thenThrow(new DuplicateKeyException("duplicate")).thenAnswer(invocation -> {
    assertThat(manager.rollbacks).isOne();
    assertThat(manager.begins).isEqualTo(2);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    return existing;
   });
   context.register(TransactionConfiguration.class);
   context.registerBean("transactionManager", TestTransactionManager.class, () -> manager);
   context.registerBean(ExternalSalonWriter.class, () -> new ExternalSalonWriter(salons,
     mock(DesignerRepository.class), mock(SalonServiceRepository.class), mock(SalonSeedDataFactory.class),
     mock(MemberRepository.class), mock(DummyTimelineService.class), mock(CatalogIntegrityService.class)));
   context.registerBean(ExternalSalonSyncService.class, () -> new ExternalSalonSyncService(
     client, context.getBean(ExternalSalonWriter.class)));
   context.refresh();

   assertThat(context.getBean(ExternalSalonSyncService.class).syncFromKakao("hair", "Seoul"))
     .containsExactly(1);
   assertThat(manager.commits).isOne();
   assertThat(manager.rollbacks).isOne();
   verify(client, times(1)).searchSalons("hair", "Seoul", 1, 15);
  }
 }

 @Configuration
 @EnableTransactionManagement
 static class TransactionConfiguration {}

 // Exercises Spring's real transaction interceptor without opening a database connection.
 static class TestTransactionManager extends AbstractPlatformTransactionManager {
  private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
  int begins;
  int commits;
  int rollbacks;
  @Override protected Object doGetTransaction() { return new Object(); }
  @Override protected boolean isExistingTransaction(Object transaction) { return active.get(); }
  @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active.set(true); begins++; }
  @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
  @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
  @Override protected Object doSuspend(Object transaction) { active.set(false); return Boolean.TRUE; }
  @Override protected void doResume(Object transaction, Object suspendedResources) { active.set(true); }
  @Override protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }
 }
}

package com.hairsalonproject2.catalog;

import com.hairsalonproject2.common.catalog.CatalogConflictException;
import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.entity.DesignerLike;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.MemberService;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.entity.SalonLike;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogIntegrityMySqlTest extends CatalogMySqlTestSupport {
 @Autowired CatalogIntegrityService integrity;
 @Autowired DesignerQueryService designers;
 @Autowired SalonServiceQueryService services;
 @Autowired SalonQueryService salons;
 @Autowired DesignerRepository designerRepository;
 @Autowired SalonServiceRepository serviceRepository;
 @Autowired MemberService members;

 @ParameterizedTest
 @EnumSource(ReservationStatus.class)
 void everyReservationStatusProtectsDeletionAndMovingButAllowsOtherEdits(ReservationStatus status) {
  Fixture fixture = fixture();
  tx(em -> reservation(fixture, status));

  assertThatThrownBy(() -> designers.delete(fixture.designerId())).isInstanceOf(CatalogConflictException.class);
  assertThatThrownBy(() -> services.delete(fixture.serviceId())).isInstanceOf(CatalogConflictException.class);
  assertThatThrownBy(() -> designers.update(fixture.designerId(), designerUpdate(fixture.otherSalonId())))
    .isInstanceOf(CatalogConflictException.class).extracting("field").isEqualTo("salonId");
  assertThatThrownBy(() -> services.update(fixture.serviceId(), serviceUpdate(fixture.otherSalonId())))
    .isInstanceOf(CatalogConflictException.class).extracting("field").isEqualTo("salonId");

  designers.update(fixture.designerId(), designerUpdate(fixture.salonId()));
  services.update(fixture.serviceId(), serviceUpdate(fixture.salonId()));
  assertThat(jdbc.queryForObject("select name from designer where designer_id = ?", String.class, fixture.designerId())).isEqualTo("Edited designer");
  assertThat(jdbc.queryForObject("select price from salon_service where service_id = ?", Integer.class, fixture.serviceId())).isEqualTo(24000);
  assertThat(jdbc.queryForObject("select status from reservation", String.class)).isEqualTo(status.name());
 }

 @Test
 void aReviewReferenceIndependentlyProtectsItsDesigner() {
  Fixture fixture = fixture();
  int reviewDesigner = tx(em -> {
   Designer historicalDesigner = designer(em.getReference(Salon.class, fixture.salonId()), "Historical designer");
   // A migrated review can retain a designer distinct from the reservation's current designer.
   review(reservation(fixture, ReservationStatus.COMPLETED), historicalDesigner, 5, "Historical review");
   return historicalDesigner.getDesignerId();
  });
  assertThat(jdbc.queryForObject("select count(*) from reservation where designer_id = ?", Integer.class, reviewDesigner)).isZero();
  assertThatThrownBy(() -> designers.delete(reviewDesigner)).isInstanceOf(CatalogConflictException.class);
  assertThatThrownBy(() -> designers.update(reviewDesigner, designerUpdate(fixture.otherSalonId())))
    .isInstanceOf(CatalogConflictException.class);
  assertThat(jdbc.queryForObject("select count(*) from review", Integer.class)).isOne();
 }

 @Test
 void salonDeletionChecksBothChildTypesAndUnreferencedCatalogItemsCanBeMovedAndDeleted() {
  Fixture fixture = fixture();
  assertThatThrownBy(() -> salons.delete(fixture.salonId())).isInstanceOf(CatalogConflictException.class);
  designers.update(fixture.designerId(), designerUpdate(fixture.otherSalonId()));
  assertThatThrownBy(() -> salons.delete(fixture.salonId())).isInstanceOf(CatalogConflictException.class);
  services.update(fixture.serviceId(), serviceUpdate(fixture.otherSalonId()));
  salons.delete(fixture.salonId());
  assertThatThrownBy(() -> salons.delete(fixture.otherSalonId())).isInstanceOf(CatalogConflictException.class);
  designers.delete(fixture.designerId());
  services.delete(fixture.serviceId());
  salons.delete(fixture.otherSalonId());
  assertThat(jdbc.queryForObject("select count(*) from salon", Integer.class)).isZero();
 }

 @Test
 void deletingAnUnreferencedDesignerAndSalonCascadesOnlyTheirLikes() {
  Fixture fixture = fixture();
  tx(em -> {
   Member member = em.getReference(Member.class, fixture.memberId());
   em.persist(DesignerLike.builder().member(member).designer(em.getReference(Designer.class, fixture.designerId())).build());
   em.persist(SalonLike.builder().member(member).salon(em.getReference(Salon.class, fixture.otherSalonId())).build());
   return null;
  });
  designers.delete(fixture.designerId());
  salons.delete(fixture.otherSalonId());
  assertThat(jdbc.queryForObject("select count(*) from designer_like", Integer.class)).isZero();
  assertThat(jdbc.queryForObject("select count(*) from salon_like", Integer.class)).isZero();
  assertThat(jdbc.queryForObject("select count(*) from member", Integer.class)).isOne();
  assertThat(jdbc.queryForObject("select count(*) from salon_service", Integer.class)).isOne();
 }

 @Test
 void memberLinkRequiresAnExistingDesignerRoleAndUniqueAssignmentButAcceptsSameAndBlank() {
  Fixture fixture = fixture();
  tx(em -> {
   Member valid = member("designer-member", MemberRole.DESIGNER);
   member("admin-member", MemberRole.ADMIN);
   em.find(Designer.class, fixture.designerId()).setMember(valid);
   return null;
  });
  for (String invalid : new String[]{"missing-member", "customer", "admin-member"}) {
   assertThatThrownBy(() -> tx(em -> integrity.validateDesignerMember(invalid, null)))
     .isInstanceOf(CatalogConflictException.class).extracting("field").isEqualTo("memberId");
  }
  assertThatThrownBy(() -> tx(em -> integrity.validateDesignerMember("designer-member", null)))
    .isInstanceOf(CatalogConflictException.class).extracting("field").isEqualTo("memberId");
  assertThat(this.<String>tx(em -> integrity.validateDesignerMember(" designer-member ", fixture.designerId()).getMemberId()))
    .isEqualTo("designer-member");
  assertThat(this.<Member>tx(em -> integrity.validateDesignerMember("  ", fixture.designerId()))).isNull();
  assertThat(this.<Member>tx(em -> integrity.validateDesignerMember(null, fixture.designerId()))).isNull();

  DesignerUpdateRequest same = designerUpdate(fixture.salonId());
  same.setMemberId("designer-member");
  assertThatCode(() -> designers.update(fixture.designerId(), same)).doesNotThrowAnyException();
  same.setMemberId("");
  designers.update(fixture.designerId(), same);
  assertThat(jdbc.queryForObject("select member_id from designer where designer_id = ?", String.class, fixture.designerId())).isNull();
 }

 enum ConcurrentMutation { DELETE_DESIGNER, MOVE_DESIGNER, DELETE_SERVICE, MOVE_SERVICE }

 @Test
 void twoDesignersCannotClaimOneMemberEvenWhenTheLoserReadAnEarlierSnapshot() throws Exception {
  Fixture fixture = fixture();
  int otherDesignerId = tx(em -> {
   member("shared-member", MemberRole.DESIGNER);
   return designer(em.getReference(Salon.class, fixture.salonId()), "Second designer").getDesignerId();
  });
  CountDownLatch oldSnapshotRead = new CountDownLatch(1);
  CountDownLatch firstLinkWritten = new CountDownLatch(1);
  CountDownLatch secondLinkStarted = new CountDownLatch(1);
  CountDownLatch commitFirstLink = new CountDownLatch(1);
  try (var workers = Executors.newFixedThreadPool(2)) {
   var second = workers.submit(() -> tx(em -> {
    assertThat(jdbc.queryForObject("select count(*) from designer where member_id = 'shared-member'", Integer.class)).isZero();
    oldSnapshotRead.countDown();
    await(firstLinkWritten);
    secondLinkStarted.countDown();
    designers.update(otherDesignerId, memberUpdate(fixture.salonId(), "shared-member"));
    return null;
   }));
   var first = workers.submit(() -> tx(em -> {
    await(oldSnapshotRead);
    designers.update(fixture.designerId(), memberUpdate(fixture.salonId(), "shared-member"));
    em.flush();
    firstLinkWritten.countDown();
    await(commitFirstLink);
    return null;
   }));
   try {
    await(secondLinkStarted);
    assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
   } finally {
    commitFirstLink.countDown();
   }
   first.get(10, TimeUnit.SECONDS);
   assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CatalogConflictException.class);
  } finally {
   oldSnapshotRead.countDown();
   firstLinkWritten.countDown();
   commitFirstLink.countDown();
  }
  assertThat(jdbc.queryForObject("select designer_id from designer where member_id = 'shared-member'", Integer.class))
    .isEqualTo(fixture.designerId());
 }

 @Test
 void simultaneousAttemptsToSwapAlreadyLinkedMembersFailWithoutDeadlock() throws Exception {
  Fixture fixture = fixture();
  int otherDesigner = tx(em -> {
   em.find(Designer.class, fixture.designerId()).setMember(member("first-member", MemberRole.DESIGNER));
   Designer second = designer(em.getReference(Salon.class, fixture.salonId()), "Second designer");
   second.setMember(member("second-member", MemberRole.DESIGNER));
   return second.getDesignerId();
  });
  CountDownLatch ready = new CountDownLatch(2);
  try (var workers = Executors.newFixedThreadPool(2)) {
   var first = workers.submit(() -> tx(em -> {
    ready.countDown();
    await(ready);
    designers.update(fixture.designerId(), memberUpdate(fixture.salonId(), "second-member"));
    return null;
   }));
   var second = workers.submit(() -> tx(em -> {
    ready.countDown();
    await(ready);
    designers.update(otherDesigner, memberUpdate(fixture.salonId(), "first-member"));
    return null;
   }));
   assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CatalogConflictException.class);
   assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CatalogConflictException.class);
  }
  assertThat(jdbc.queryForObject("select member_id from designer where designer_id = ?", String.class, fixture.designerId())).isEqualTo("first-member");
  assertThat(jdbc.queryForObject("select member_id from designer where designer_id = ?", String.class, otherDesigner)).isEqualTo("second-member");
 }

 enum FirstMemberChange { LINK, ROLE }

 @ParameterizedTest
 @EnumSource(FirstMemberChange.class)
 void memberLinkAndRoleChangeSerializeAndValidateTheCommittedState(FirstMemberChange firstChange) throws Exception {
  Fixture fixture = fixture();
  tx(em -> member("racing-member", MemberRole.DESIGNER));
  CountDownLatch oldMemberLoaded = new CountDownLatch(1);
  CountDownLatch firstChangeWritten = new CountDownLatch(1);
  CountDownLatch secondChangeStarted = new CountDownLatch(1);
  CountDownLatch commitFirstChange = new CountDownLatch(1);
  try (var workers = Executors.newFixedThreadPool(2)) {
   var first = workers.submit(() -> tx(em -> {
    await(oldMemberLoaded);
    if (firstChange == FirstMemberChange.LINK) designers.update(fixture.designerId(), memberUpdate(fixture.salonId(), "racing-member"));
    else members.changeMemberRoleByAdmin("administrator", "racing-member", MemberRole.USER);
    em.flush();
    firstChangeWritten.countDown();
    await(commitFirstChange);
    return null;
   }));
   var second = workers.submit(() -> tx(em -> {
    Member previouslyLoaded = em.find(Member.class, "racing-member");
    assertThat(previouslyLoaded.getRole()).isEqualTo(MemberRole.DESIGNER);
    assertThat(previouslyLoaded.getDesigner()).isNull();
    oldMemberLoaded.countDown();
    await(firstChangeWritten);
    secondChangeStarted.countDown();
    if (firstChange == FirstMemberChange.LINK) members.changeMemberRoleByAdmin("administrator", "racing-member", MemberRole.USER);
    else designers.update(fixture.designerId(), memberUpdate(fixture.salonId(), "racing-member"));
    return null;
   }));
   try {
    await(secondChangeStarted);
    assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
   } finally {
    commitFirstChange.countDown();
   }
   first.get(10, TimeUnit.SECONDS);
   assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
     .hasCauseInstanceOf(firstChange == FirstMemberChange.LINK ? BusinessException.class : CatalogConflictException.class);
  } finally {
   oldMemberLoaded.countDown();
   firstChangeWritten.countDown();
   commitFirstChange.countDown();
  }
  assertThat(jdbc.queryForObject("select role from member where member_id = 'racing-member'", String.class))
    .isEqualTo(firstChange == FirstMemberChange.LINK ? "DESIGNER" : "USER");
  assertThat(jdbc.queryForObject("select member_id from designer where designer_id = ?", String.class, fixture.designerId()))
    .isEqualTo(firstChange == FirstMemberChange.LINK ? "racing-member" : null);
 }

 enum MovedCatalogItem { DESIGNER, SERVICE }

 @ParameterizedTest
 @EnumSource(MovedCatalogItem.class)
 void lockingRefreshesAPreviouslyLoadedCatalogEntityBeforeCheckingHistory(MovedCatalogItem item) throws Exception {
  Fixture fixture = fixture();
  CountDownLatch entityLoaded = new CountDownLatch(1);
  CountDownLatch moveAndReservationCommitted = new CountDownLatch(1);
  try (var worker = Executors.newSingleThreadExecutor()) {
   var staleChange = worker.submit(() -> tx(em -> {
    if (item == MovedCatalogItem.DESIGNER) em.find(Designer.class, fixture.designerId());
    else em.find(SalonService.class, fixture.serviceId());
    entityLoaded.countDown();
    await(moveAndReservationCommitted);
    if (item == MovedCatalogItem.DESIGNER) designers.update(fixture.designerId(), designerUpdate(fixture.salonId()));
    else services.update(fixture.serviceId(), serviceUpdate(fixture.salonId()));
    return null;
   }));
   try {
    await(entityLoaded);
    tx(em -> {
     Salon target = em.getReference(Salon.class, fixture.otherSalonId());
     Member customer = em.getReference(Member.class, fixture.memberId());
     if (item == MovedCatalogItem.DESIGNER) {
      designers.update(fixture.designerId(), designerUpdate(fixture.otherSalonId()));
      reservation(customer, em.find(Designer.class, fixture.designerId()), service(target, "Moved cut", 20000, 30), ReservationStatus.RESERVED);
     } else {
      services.update(fixture.serviceId(), serviceUpdate(fixture.otherSalonId()));
      reservation(customer, designer(target, "Moved designer"), em.find(SalonService.class, fixture.serviceId()), ReservationStatus.RESERVED);
     }
     return null;
    });
   } finally {
    moveAndReservationCommitted.countDown();
   }
   assertThatThrownBy(() -> staleChange.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CatalogConflictException.class);
  } finally {
   moveAndReservationCommitted.countDown();
  }
  assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isOne();
 }

 @ParameterizedTest
 @EnumSource(ConcurrentMutation.class)
 void reservationCommitIsVisibleToWaitingMutationEvenAfterRepeatableReadSnapshot(ConcurrentMutation mutation) throws Exception {
  Fixture fixture = fixture();
  CountDownLatch snapshotRead = new CountDownLatch(1);
  CountDownLatch readLocksHeld = new CountDownLatch(1);
  CountDownLatch mutationStarted = new CountDownLatch(1);
  CountDownLatch allowReservationCommit = new CountDownLatch(1);

  try (var workers = Executors.newFixedThreadPool(2)) {
   var mutationResult = workers.submit(() -> tx(em -> {
    assertThat(jdbc.queryForObject("select @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
    assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isZero();
    snapshotRead.countDown();
    await(readLocksHeld);
    mutationStarted.countDown();
    switch (mutation) {
     case DELETE_DESIGNER -> designers.delete(fixture.designerId());
     case MOVE_DESIGNER -> designers.update(fixture.designerId(), designerUpdate(fixture.otherSalonId()));
     case DELETE_SERVICE -> services.delete(fixture.serviceId());
     case MOVE_SERVICE -> services.update(fixture.serviceId(), serviceUpdate(fixture.otherSalonId()));
    }
    return null;
   }));
   var reservationResult = workers.submit(() -> tx(em -> {
    await(snapshotRead);
    // The production reservation flow acquires these two repository locks in this order.
    designerRepository.findByIdForReservation(fixture.designerId()).orElseThrow();
    serviceRepository.findByIdForReservation(fixture.serviceId()).orElseThrow();
    readLocksHeld.countDown();
    await(allowReservationCommit);
    return reservation(fixture, ReservationStatus.RESERVED).getReservationId();
   }));
   try {
    await(mutationStarted);
    assertThatThrownBy(() -> mutationResult.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
   } finally {
    allowReservationCommit.countDown();
   }
   assertThat(reservationResult.get(10, TimeUnit.SECONDS)).isPositive();
   assertThatThrownBy(() -> mutationResult.get(10, TimeUnit.SECONDS))
     .hasCauseInstanceOf(CatalogConflictException.class);
  } finally {
   // Also release workers when a setup assertion fails, keeping the test suite bounded.
   snapshotRead.countDown();
   readLocksHeld.countDown();
   allowReservationCommit.countDown();
  }
  assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isOne();
  assertThat(jdbc.queryForObject("select salon_id from designer where designer_id = ?", Integer.class, fixture.designerId()))
    .isEqualTo(fixture.salonId());
  assertThat(jdbc.queryForObject("select salon_id from salon_service where service_id = ?", Integer.class, fixture.serviceId()))
    .isEqualTo(fixture.salonId());
 }

 private static void await(CountDownLatch latch) {
  try {
   if (!latch.await(8, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for the other transaction");
  } catch (InterruptedException exception) {
   Thread.currentThread().interrupt();
   throw new AssertionError(exception);
  }
 }

 private static DesignerUpdateRequest designerUpdate(int salonId) {
  var request = new DesignerUpdateRequest();
  request.setSalonId(salonId);
  request.setName("Edited designer");
  request.setCareerYears(7);
  return request;
 }

 private static DesignerUpdateRequest memberUpdate(int salonId, String memberId) {
  DesignerUpdateRequest request = designerUpdate(salonId);
  request.setMemberId(memberId);
  return request;
 }

 private static SalonServiceUpdateRequest serviceUpdate(int salonId) {
  var request = new SalonServiceUpdateRequest();
  request.setSalonId(salonId);
  request.setName("Edited service");
  request.setPrice(24000);
  request.setDuration(45);
  return request;
 }
}

package com.hairsalonproject2.common.catalog;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.common.exception.ErrorCode;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Guards run in the caller's write transaction, including the subsequent change. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CatalogIntegrityService {
 private final EntityManager entityManager;
 private final JdbcTemplate jdbc;

 public void assertSalonDeletable(Integer id) {
  lock(Salon.class, id);
  if (exists("select designer_id from designer where salon_id = ? limit 1 for share", id)
    || exists("select service_id from salon_service where salon_id = ? limit 1 for share", id)) {
   throw new CatalogConflictException(null, "디자이너 또는 시술이 등록된 미용실은 삭제할 수 없습니다.");
  }
 }

 public void assertDesignerDeletable(Integer id) {
  lock(Designer.class, id);
  if (hasDesignerHistory(id)) {
   throw new CatalogConflictException(null, "예약 또는 리뷰 이력이 있는 디자이너는 삭제할 수 없습니다.");
  }
 }

 public void assertServiceDeletable(Integer id) {
  lock(SalonService.class, id);
  if (hasServiceHistory(id)) {
   throw new CatalogConflictException(null, "예약 이력이 있는 시술은 삭제할 수 없습니다.");
  }
 }

 public void assertDesignerMoveAllowed(Integer id, Integer targetSalonId) {
  Designer designer = lock(Designer.class, id);
  if (!Objects.equals(designer.getSalon().getSalonId(), targetSalonId) && hasDesignerHistory(id)) {
   throw new CatalogConflictException("salonId", "예약 또는 리뷰 이력이 있는 디자이너의 소속 미용실은 변경할 수 없습니다.");
  }
 }

 public void assertServiceMoveAllowed(Integer id, Integer targetSalonId) {
  SalonService service = lock(SalonService.class, id);
  if (!Objects.equals(service.getSalon().getSalonId(), targetSalonId) && hasServiceHistory(id)) {
   throw new CatalogConflictException("salonId", "예약 이력이 있는 시술의 소속 미용실은 변경할 수 없습니다.");
  }
 }

 public Member validateDesignerMember(String memberId, Integer currentDesignerId) {
  if (memberId == null || memberId.isBlank()) return null;
  String normalized = memberId.trim();
  Member member = entityManager.find(Member.class, normalized, LockModeType.PESSIMISTIC_WRITE);
  if (member == null) throw new CatalogConflictException("memberId", "존재하는 회원 ID를 입력해 주세요.");
  // A caller may already have loaded this member before waiting for its lock.
  entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);
  if (member.getRole() != MemberRole.DESIGNER) {
   throw new CatalogConflictException("memberId", "디자이너 권한을 가진 회원만 연결할 수 있습니다.");
  }
  var linkedIds = jdbc.queryForList("select designer_id from designer where member_id = ? for share", Integer.class, normalized);
  if (linkedIds.stream().anyMatch(id -> !Objects.equals(id, currentDesignerId))) {
   throw new CatalogConflictException("memberId", "이미 다른 디자이너에게 연결된 회원입니다.");
  }
  return member;
 }

 /** The caller must acquire the member WRITE lock before checking its current links. */
 public void assertDesignerMemberRoleChangeAllowed(String memberId, MemberRole role) {
  if (role != MemberRole.DESIGNER && !jdbc.queryForList(
    "select designer_id from designer where member_id = ? for share", Integer.class, memberId).isEmpty()) {
   throw new BusinessException(ErrorCode.CONNECTED_DESIGNER_ACCOUNT_ROLE_CHANGE_NOT_ALLOWED);
  }
 }

 private boolean hasDesignerHistory(Integer id) {
  return exists("select reservation_id from reservation where designer_id = ? limit 1 for share", id)
    || exists("select review_id from review where designer_id = ? limit 1 for share", id);
 }

 private boolean hasServiceHistory(Integer id) {
  return exists("select reservation_id from reservation where service_id = ? limit 1 for share", id);
 }

 private boolean exists(String sql, Integer id) {
  // Locking reads see commits made while waiting for the parent lock, even under MySQL REPEATABLE READ.
  return !jdbc.queryForList(sql, Integer.class, id).isEmpty();
 }

 private <T> T lock(Class<T> type, Integer id) {
  T entity = id == null ? null : entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE);
  if (entity == null) throw new EntityNotFoundException(type.getSimpleName() + " not found: " + id);
  // An existing persistence-context entry may predate the transaction we waited for.
  entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
  return entity;
 }
}

package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class ExternalSalonSyncService {
 private final KakaoLocalSearchClient kakaoLocalSearchClient;
 private final ExternalSalonWriter externalSalonWriter;

 // Suspend a caller's transaction too: network delays must not hold database locks.
 @Transactional(propagation = Propagation.NOT_SUPPORTED)
 public List<Integer> syncFromKakao(String keyword, String region) {
  var places = kakaoLocalSearchClient.searchSalons(keyword, region, 1, 15);
  for (int attempt = 1; attempt <= 3; attempt++) {
   try {
    return externalSalonWriter.saveKakaoPlaces(places);
   } catch (DataIntegrityViolationException | PessimisticLockingFailureException | ExternalSalonWriteConflictException ex) {
    if (ex instanceof DataIntegrityViolationException && !isDuplicateKey(ex)) throw ex;
    if (attempt == 3) throw new ExternalSalonSyncConflictException(ex);
   }
  }
  throw new IllegalStateException("Unreachable sync retry state");
 }

 private boolean isDuplicateKey(Throwable failure) {
  for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
   if (cause instanceof DuplicateKeyException) return true;
   if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
  }
  return false;
 }
}

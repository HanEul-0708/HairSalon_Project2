package com.hairsalonproject2.salon.service;

/** Concurrent catalog changes prevented a sync from completing after bounded retries. */
public class ExternalSalonSyncConflictException extends RuntimeException {
 public ExternalSalonSyncConflictException(Throwable cause) {
  super("다른 요청이 미용실 또는 회원 정보를 변경하고 있습니다. 잠시 후 다시 동기화해 주세요.", cause);
 }
}

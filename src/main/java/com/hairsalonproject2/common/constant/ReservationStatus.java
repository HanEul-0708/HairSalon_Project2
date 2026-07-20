package com.hairsalonproject2.common.constant;

/*
 * ReservationStatus
 *
 * reservation 테이블 status 컬럼 ENUM과 매핑
 *
 * DB:
 * ENUM('RESERVED', 'CANCELLED', 'COMPLETED')
 */
public enum ReservationStatus {

 /*
  * 예약 완료 상태
  */
 RESERVED,

 /*
  * 예약 취소
  */
 CANCELLED,

 /*
  * 시술 완료
  */
 COMPLETED
}
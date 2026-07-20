package com.hairsalonproject2.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/*
 * BaseTimeEntity
 *
 * created_at + updated_at 를 같이 공통으로 사용하는 엔티티들의 부모 클래스
 *
 * 예:
 * - Salon
 * - Reservation
 * - Board
 *
 * 핵심:
 * 1. 이 클래스 자체가 테이블은 아님
 * 2. 상속받는 엔티티가 생성일시 / 수정일시 컬럼을 같이 물려받음
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

 /*
  * 생성일시
  *
  * 엔티티가 처음 INSERT 될 때 자동으로 현재 시간이 저장된다.
  * 이후 수정 시에는 변경되지 않는다.
  */
 @CreationTimestamp
 @Column(name = "created_at", updatable = false)
 private LocalDateTime createdAt;

 /*
  * 수정일시
  *
  * 엔티티가 UPDATE 될 때마다 자동으로 현재 시간으로 갱신된다.
  */
 @UpdateTimestamp
 @Column(name = "updated_at")
 private LocalDateTime updatedAt;
}
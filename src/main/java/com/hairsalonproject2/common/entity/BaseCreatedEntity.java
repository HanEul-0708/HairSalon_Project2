package com.hairsalonproject2.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/*
 * BaseCreatedEntity
 *
 * created_at 컬럼만 공통으로 사용하는 엔티티들의 부모 클래스
 *
 * 예:
 * - Member
 * - Designer
 * - SalonService
 * - ReservationSlot
 * - ReviewImage
 * - BoardImage
 * - BoardFile
 * - SalonLike
 *
 * 핵심:
 * 1. 이 클래스 자체가 테이블이 되는 것은 아님
 * 2. 상속받는 엔티티가 created_at 컬럼을 물려받음
 */
@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

 /*
  * 생성일시
  *
  * DB의 created_at 컬럼과 매핑된다.
  * 엔티티가 처음 저장될 때 Hibernate가 자동으로 현재 시간을 넣어준다.
  *
  * updatable = false
  * -> 한 번 생성된 뒤에는 수정되지 않도록 설정
  */
 @CreationTimestamp
 @Column(name = "created_at", updatable = false)
 private LocalDateTime createdAt;
}
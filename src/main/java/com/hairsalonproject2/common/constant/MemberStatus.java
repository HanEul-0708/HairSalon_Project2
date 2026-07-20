package com.hairsalonproject2.common.constant;

import lombok.Getter;

/**
 * 회원 상태
 * <p>
 * ACTIVE   : 정상 이용 가능
 * INACTIVE : 비활성 상태
 * DELETED  : 탈퇴 처리된 상태
 */
@Getter
public enum MemberStatus {

 ACTIVE("활성"), INACTIVE("비활성"), DELETED("탈퇴");

 private final String description;

 MemberStatus(String description) {
  this.description = description;
 }
}

package com.hairsalonproject2.common.constant;

import lombok.Getter;

@Getter
public enum PaymentMethod {

 CARD("카드"), CASH("현금"), KAKAO_PAY("카카오페이"), NAVER_PAY("네이버페이");

 private final String description;

 PaymentMethod(String description) {
  this.description = description;
 }
}

package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.entity.Salon;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

public final class SalonSpecifications {

 public static Specification<Salon> bySearch(SalonSearchRequest request, List<Integer> keywordMatchedSalonIds) {
  return Specification.<Salon>unrestricted().and(keywordContains(request.getKeyword(), keywordMatchedSalonIds)).and(regionContains(request.getRegion())).and(minRatingAtLeast(request.getMinRating())).and(reservableEquals(request.getReservable()));
 }

 private static Specification<Salon> keywordContains(String keyword, List<Integer> keywordMatchedSalonIds) { if (keyword == null || keyword.isBlank()) return null;
  return (root, query, cb) -> {
   String pattern = "%" + keyword.toLowerCase() + "%";
   if (keywordMatchedSalonIds == null || keywordMatchedSalonIds.isEmpty())
	return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("address")), pattern), cb.like(cb.lower(root.get("roadAddress")), pattern), cb.like(cb.lower(root.get("description")), pattern));
   return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("address")), pattern), cb.like(cb.lower(root.get("roadAddress")), pattern), cb.like(cb.lower(root.get("description")), pattern), root.get("salonId").in(keywordMatchedSalonIds));
  };//전달된 키워드가 없거나 공백이면 null을 반환하여 쿼리 조건에서 제외합니다. 대소문자를 구분하지 않도록 키워드를 소문자로 변환하고, SQL의 LIKE 연산자를 위해 앞뒤로 %를 붙여 매칭 패턴을 생성합니다. 조건 결합: 미용실 엔티티의 name(이름), address(지번 주소), roadAddress(도로명 주소), description(설명) 중 하나라도 키워드를 포함하고 있으면 매칭되도록 OR 조건으로 묶습니다. 외부 매칭 ID 포함: 사전에 키워드로 매칭된 미용실 ID 목록(keywordMatchedSalonIds)이 존재하는 경우, 위의 4가지 필드 조건 외에 salonId IN (keywordMatchedSalonIds) 조건을 OR로 추가하여 서비스 키워드로 검색된 미용실까지 함께 결과에 포함시킵니다.
 }

 private static Specification<Salon> regionContains(String region) {
  if (region == null || region.isBlank()) return null;
  return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("address")), "%" + region.toLowerCase() + "%"), cb.like(cb.lower(root.get("roadAddress")), "%" + region.toLowerCase() + "%"));
 }//전달된 지역명(region)이 없거나 공백이면 null을 반환합니다. 지역명 문자열을 소문자로 변환하고 % 패턴을 적용합니다. 미용실 엔티티의 address(지번 주소) 또는 roadAddress(도로명 주소) 필드에 해당 지역명이 포함되어 있는지 확인하며, 두 필드 중 하나라도 일치하면 조건이 충족되도록 OR 연산으로 처리합니다.

 private static Specification<Salon> minRatingAtLeast(BigDecimal minRating) {
  if (minRating == null) return null; return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
 }//전달된 최소 평점(minRating)이 null이면 조건을 생성하지 않고 null을 반환합니다. 데이터베이스의 averageRating(평균 평점) 필드 값이 입력받은 minRating보다 크거나 같은 경우(greaterThanOrEqualTo)에만 조회 결과에 포함되도록 설정합니다.

 private static Specification<Salon> reservableEquals(Boolean reservable) {
  if (reservable == null) return null; return (root, query, cb) -> cb.equal(root.get("reservable"), reservable);
 }//전달된 예약 가능 여부(reservable 불리언 값)가 null이면 조건을 생성하지 않고 null을 반환합니다. 미용실 엔티티의 reservable 필드 값과 사용자가 요청한 true 또는 false 값이 정확히 일치하는지(equal) 비교하여 필터링합니다.
}

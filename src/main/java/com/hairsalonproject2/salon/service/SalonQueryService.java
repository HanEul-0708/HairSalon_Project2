package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonDetailResponse;
import com.hairsalonproject2.salon.dto.response.SalonRankResponse;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.entity.SalonLike;
import com.hairsalonproject2.salon.repository.SalonLikeRepository;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonQueryService {

 private final SalonRepository salonRepository;
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final SalonLikeRepository salonLikeRepository;
 private final MemberRepository memberRepository;
 private final ReviewRepository reviewRepository;

 public List<SalonSummaryResponse> search(SalonSearchRequest request) {
  return search(request, 0, Integer.MAX_VALUE).getContent();
 }//페이징 없이 전체 검색 결과를 List<SalonSummaryResponse> 형태로 반환합니다. 내부적으로 페이지 번호 0, 사이즈는 정수의 최댓값으로 설정하여 페이징 메소드를 호출합니다.

 public Page<SalonSummaryResponse> search(SalonSearchRequest request, int page, int size) {
  SalonSearchRequest safeRequest = request == null ? new SalonSearchRequest() : request;
  List<SalonSummaryResponse> responses = searchInternal(safeRequest);
  int safeSize = Math.max(size, 1);
  int maxPage = responses.isEmpty() ? 0 : (responses.size() - 1) / safeSize;
  int safePage = Math.min(Math.max(page, 0), maxPage);
  int fromIndex = Math.min(safePage * safeSize, responses.size());
  int toIndex = Math.min(fromIndex + safeSize, responses.size());
  return new PageImpl<>(responses.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), responses.size());
 }//요청된 페이지 번호와 사이즈를 기반으로 페이징 처리가 된 Page<SalonSummaryResponse>를 반환합니다. 안전한 인덱스 계산을 통해 범위를 벗어나지 않도록 제어합니다.

 private List<SalonSummaryResponse> searchInternal(SalonSearchRequest request) {
  List<Integer> keywordMatchedSalonIds = resolveKeywordMatchedSalonIds(request);
  List<SalonSummaryResponse> responses = salonRepository.findAll(SalonSpecifications.bySearch(request, keywordMatchedSalonIds)).stream().map(this::toSummary).collect(Collectors.toCollection(ArrayList::new));
  String sort = resolveSort(request);
  if ("rating".equals(sort))
   responses.sort(Comparator.comparing(SalonSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonSummaryResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())));
  else if ("likes".equals(sort))
   responses.sort(Comparator.comparing(SalonSummaryResponse::getLikeCount, Comparator.nullsLast(Comparator.reverseOrder())));
  else
   responses.sort(Comparator.comparing(SalonSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonSummaryResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonSummaryResponse::getLikeCount, Comparator.nullsLast(Comparator.reverseOrder())));
  return responses;
 }//실제 검색의 핵심 로직을 수행하는 내부 메소드입니다. 키워드 매칭을 통해 미용실 ID 목록을 조회한 후, 조건에 맞는 데이터를 데이터베이스에서 가져옵니다. 요청된 정렬 기준(rating, likes, recommended)에 따라 결과를 정렬합니다.

 public SalonDetailResponse getDetail(Integer salonId) {
  Salon salon = salonRepository.findById(salonId).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));//전달받은 salonId로 미용실을 조회하며, 데이터가 없을 경우 EntityNotFoundException을 발생시킵니다. 해당 미용실에 소속된 디자이너 목록을 조회하여 각 디자이너의 평균 평점 및 리뷰 수를 매핑합니다. 미용실에서 제공하는 서비스 목록을 조회해서 미용실 정보, 디자이너 정보, 서비스 정보를 합산하여 SalonDetailResponse 객체로 최종 반환합니다.
  Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows().stream().collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));
  List<DesignerSummaryResponse> designers = designerRepository.findBySalonSalonId(salonId).stream().map(d -> {
   DesignerRatingRow row = ratings.get(d.getDesignerId());
   return DesignerSummaryResponse.builder().designerId(d.getDesignerId()).salonId(salonId).salonName(salon.getName()).name(d.getName()).profileImage(d.getProfileImage()).careerYears(d.getCareerYears()).averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating())).reviewCount(row == null ? 0L : row.getReviewCount()).build();
  }).toList();
  List<SalonServiceSummaryResponse> services = salonServiceRepository.findBySalonSalonId(salonId).stream().map(s -> SalonServiceSummaryResponse.builder().serviceId(s.getServiceId()).salonId(salonId).salonName(salon.getName()).name(s.getName()).price(s.getPrice()).duration(s.getDuration()).build()).toList();
  return SalonDetailResponse.builder().salonId(salon.getSalonId()).externalId(salon.getExternalId()).sourceType(salon.getSourceType()).name(salon.getName()).address(salon.getAddress()).roadAddress(salon.getRoadAddress()).phone(salon.getPhone()).description(salon.getDescription()).imageUrl(salon.getImageUrl()).placeUrl(salon.getPlaceUrl()).reservable(salon.getReservable()).averageRating(salon.getAverageRating()).reviewCount(salon.getReviewCount()).likeCount(salon.getLikeCount()).designers(designers).services(services).build();
 }

 @Transactional
 public boolean like(Integer salonId, String memberId) {
  Salon salon = salonRepository.findById(salonId).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));
  Member member = memberRepository.findById(memberId).orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
  if (salonLikeRepository.findByMember_MemberIdAndSalon_SalonId(memberId, salonId).isPresent()) return false;
  salonLikeRepository.save(SalonLike.builder().member(member).salon(salon).build());
  salon.setLikeCount(Math.toIntExact(salonLikeRepository.countBySalon_SalonId(salonId)));
  return true;//회원이 미용실에 좋아요를 추가합니다. 이미 생성된 내역이 있다면 false를 반환하고, 없다면 좋아요 데이터를 저장한 뒤 미용실 엔티티의 전체 좋아요 수(likeCount)를 갱신합니다.
 }

 @Transactional
 public boolean unlike(Integer salonId, String memberId) {
  Salon salon = salonRepository.findById(salonId).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));
  return salonLikeRepository.findByMember_MemberIdAndSalon_SalonId(memberId, salonId).map(like -> {
   salonLikeRepository.delete(like);
   salon.setLikeCount(Math.toIntExact(salonLikeRepository.countBySalon_SalonId(salonId)));
   return true;
  }).orElse(false);
 }//회원이 미용실에 누른 좋아요를 취소합니다. 기존에 존재하는 좋아요 데이터를 찾아 삭제한 후, 미용실 엔티티의 전체 좋아요 수(likeCount)를 감소된 수치로 갱신합니다.

 public List<SalonRankResponse> recommendedTop3() {
  List<Salon> salons = salonRepository.findRecommendedSalons(PageRequest.of(0, 3)); List<SalonRankResponse> result = new ArrayList<>();
  for (int i = 0; i < salons.size(); i++) { Salon salon = salons.get(i);
   result.add(SalonRankResponse.builder().rank(i + 1).salonId(salon.getSalonId()).salonName(salon.getName()).averageRating(salon.getAverageRating()).reviewCount(salon.getReviewCount()).likeCount(salon.getLikeCount()).build());
  } return result;//salonRepository.findRecommendedSalons를 호출하여 추천 기준에 부합하는 미용실 데이터를 최대 3개까지 가져옵니다. 조회된 순서대로 순위(1등부터 3등까지)를 연산하여 rank 필드에 주입하고, SalonRankResponse 리스트 형태로 가공하여 반환합니다.
 }

 @Transactional
 public void refreshStatistics(Integer salonId) {
  Salon salon = salonRepository.findById(salonId).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId)); List<Review> reviews = reviewRepository.findByDesigner_Salon_SalonId(salonId); if (reviews.isEmpty()) {
   salon.setAverageRating(BigDecimal.ZERO); salon.setReviewCount(0); return; }
  double avg = reviews.stream().mapToInt(review -> review.getRating() == null ? 0 : review.getRating()).average().orElse(0.0); salon.setAverageRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)); salon.setReviewCount(reviews.size());//엔티티 검증: salonId에 해당하는 미용실을 조회하고, 데이터가 없으면 EntityNotFoundException을 발생시킵니다. 데이터 취합 및 예외 처리: 소속 디자이너들에게 누적된 전체 리뷰 목록을 가져오며, 리뷰가 존재하지 않으면 평점과 리뷰 수를 각각 0으로 초기화한 뒤 종료합니다. 연산 및 반영: 리뷰가 존재할 경우 평점 평균을 계산한 뒤 소수점 아래 둘째 자리로 반올림(RoundingMode.HALF_UP) 처리하여 총 리뷰 개수와 함께 미용실 엔티티에 직접 갱신합니다.
 }

 public List<SalonSummaryResponse> getLikedSalons(String memberId) {
  return salonLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream().map(SalonLike::getSalon).map(this::toSummary).toList();
 }//특정 회원이 좋아요를 누른 미용실 목록을 생성일 역순(최신순)으로 조회하여 요약 객체로 반환합니다.

 public List<Integer> getLikedSalonIds(String memberId) {
  return salonLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream().map(like -> like.getSalon().getSalonId()).toList();
 }//특정 회원이 좋아요를 누른 미용실의 식별자(ID) 목록만 추출하여 반환합니다.

 public boolean isLikedByMember(Integer salonId, String memberId) {
  if (memberId == null || memberId.isBlank()) return false;
  return salonLikeRepository.existsByMember_MemberIdAndSalon_SalonId(memberId, salonId);
 }//특정 회원이 해당 미용실에 좋아요를 눌렀는지 여부를 이진값(boolean)으로 확인합니다.

 @Transactional
 public Integer create(SalonCreateRequest request) {
  Salon salon = Salon.builder().externalId(request.getExternalId()).sourceType(request.getSourceType()).name(request.getName()).address(request.getAddress()).roadAddress(request.getRoadAddress()).phone(request.getPhone()).description(request.getDescription()).imageUrl(request.getImageUrl()).placeUrl(request.getPlaceUrl()).reservable(request.getReservable() != null ? request.getReservable() : Boolean.TRUE).build();
  return salonRepository.save(salon).getSalonId();
 }

 @Transactional
 public void update(Integer salonId, SalonUpdateRequest request) {
  Salon salon = salonRepository.findById(salonId).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));
  salon.setName(request.getName());
  salon.setAddress(request.getAddress());
  salon.setRoadAddress(request.getRoadAddress());
  salon.setPhone(request.getPhone());
  salon.setDescription(request.getDescription());
  salon.setImageUrl(request.getImageUrl());
  salon.setPlaceUrl(request.getPlaceUrl());
  salon.setReservable(request.getReservable() != null ? request.getReservable() : Boolean.TRUE);
 }

 @Transactional
 public void delete(Integer salonId) {
  salonRepository.deleteById(salonId);
 }

 private List<Integer> resolveKeywordMatchedSalonIds(SalonSearchRequest request) {
  if (request == null || !request.isServiceKeywordSearchEnabled()) return List.of();
  String keyword = request.getKeyword();
  if (keyword == null || keyword.isBlank()) return List.of();
  return salonServiceRepository.findDistinctSalonIdsByKeyword(keyword);
 }

 private String resolveSort(SalonSearchRequest request) {
  if (request.getSort() == null || request.getSort().isBlank()) return "recommended";
  return switch (request.getSort().toLowerCase()) {
   case "rating", "likes", "recommended" -> request.getSort().toLowerCase();
   default -> "recommended";
  };
 }

 private SalonSummaryResponse toSummary(Salon salon) {
  return SalonSummaryResponse.builder().salonId(salon.getSalonId()).name(salon.getName()).address(salon.getAddress()).roadAddress(salon.getRoadAddress()).phone(salon.getPhone()).imageUrl(salon.getImageUrl()).averageRating(salon.getAverageRating()).reviewCount(salon.getReviewCount()).likeCount(salon.getLikeCount()).reservable(salon.getReservable()).createdAt(salon.getCreatedAt()).build();
 }
}

package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerCreateRequest;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.designer.dto.response.DesignerDetailResponse;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.entity.DesignerLike;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerLikeRepository;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignerQueryService {

 private final DesignerRepository designerRepository;
 private final DesignerLikeRepository designerLikeRepository;
 private final SalonRepository salonRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final MemberRepository memberRepository;

 public List<DesignerSummaryResponse> search(DesignerSearchRequest request) {
  DesignerSearchRequest safeRequest = request == null ? new DesignerSearchRequest() : request;
  return search(safeRequest, 0, Integer.MAX_VALUE).getContent();
 }

 public Page<DesignerSummaryResponse> search(DesignerSearchRequest request, int page, int size) {
  DesignerSearchRequest safeRequest = request == null ? new DesignerSearchRequest() : request;
  Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows().stream().collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));
  List<DesignerSummaryResponse> filtered = designerRepository.findAll(DesignerSpecifications.bySearch(safeRequest)).stream().map(d -> toSummary(d, ratings.get(d.getDesignerId()))).filter(d -> safeRequest.getMinRating() == null || d.getAverageRating().compareTo(safeRequest.getMinRating()) >= 0).filter(d -> safeRequest.getMinReviewCount() == null || d.getReviewCount() >= safeRequest.getMinReviewCount()).sorted(designerComparator(safeRequest.getSortBy())).toList();
  int safeSize = Math.max(size, 1); int maxPage = filtered.isEmpty() ? 0 : (filtered.size() - 1) / safeSize;
  int safePage = Math.min(Math.max(page, 0), maxPage); int fromIndex = Math.min(safePage * safeSize, filtered.size());
  int toIndex = Math.min(fromIndex + safeSize, filtered.size()); return new PageImpl<>(filtered.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), filtered.size());
 }//동적 조건 필터링: DesignerSearchRequest에 포함된 검색어(디자이너 이름, 소개문, 미용실 이름) 및 최소 경력 연차 등의 필터를 DesignerSpecifications.bySearch와 연동하여 동적 쿼리로 필터링합니다. 안전한 페이징 제어: 요청된 페이지 번호(page)와 사이즈(size)가 유효 범위를 벗어나 인덱스 오류가 발생하지 않도록 안전하게 보정하여 지정된 구간의 데이터만 잘라냅니다. 응답 DTO 규격화: 엔티티 데이터를 그대로 노출하지 않고 목록 화면 표현에 최적화된 요약 정보 객체인 DesignerSummaryResponse로 가공하여 최종 반환합니다.

 public DesignerDetailResponse getDetail(Integer designerId, String loginMemberId) {
  Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  DesignerRatingRow row = designerRepository.findDesignerRatingRows().stream().filter(r -> designerId.equals(r.getDesignerId())).findFirst().orElse(null);
  List<SalonServiceSummaryResponse> services = salonServiceRepository.findBySalonSalonId(designer.getSalon().getSalonId()).stream().map(s -> SalonServiceSummaryResponse.builder().serviceId(s.getServiceId()).salonId(s.getSalon().getSalonId()).salonName(s.getSalon().getName()).name(s.getName()).price(s.getPrice()).duration(s.getDuration()).build()).toList();
  return DesignerDetailResponse.builder().designerId(designer.getDesignerId()).salonId(designer.getSalon().getSalonId()).salonName(designer.getSalon().getName()).memberId(designer.getMember() != null ? designer.getMember().getMemberId() : null).name(designer.getName()).profileImage(designer.getProfileImage()).introduction(designer.getIntroduction()).careerYears(designer.getCareerYears()).averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating())).reviewCount(row == null ? 0L : row.getReviewCount()).likeCount(designer.getLikeCount() == null ? 0 : designer.getLikeCount()).likedByCurrentUser(isLikedByMember(designerId, loginMemberId)).salonServices(services).build();
 }//상세 데이터 검증 및 조회: 전달받은 designerId를 기준으로 디자이너 정보를 조회하며, 매칭되는 데이터가 없는 경우 예외를 발생시켜 일관된 에러 처리가 가능하도록 유도합니다. 회원별 개인화 상태 매핑: loginMemberId 매개변수를 식별자로 활용하여, 현재 로그인한 회원이 해당 디자이너에게 '좋아요'를 누른 상태인지 여부(좋아요 등록 상태 코드)를 조회 로직 내에서 동적으로 판별합니다. 종합 응답 객체 조립: 디자이너의 인적 사항, 경력 사항, 소속 미용실 정보 및 로그인 회원 기준의 상호작용 데이터(좋아요 여부 등)를 모두 취합하여 DesignerDetailResponse 객체 하나로 묶어 반환합니다.

 public DesignerDetailResponse getDetail(Integer designerId) {
  return getDetail(designerId, null);
 }

 public List<DesignerSummaryResponse> getLikedDesigners(String memberId) {
  return designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream().map(DesignerLike::getDesigner).map(designer -> toSummary(designer, findRatingRow(designer.getDesignerId()))).toList();
 }

 public List<Integer> getLikedDesignerIds(String memberId) {
  return designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream().map(like -> like.getDesigner().getDesignerId()).toList();
 }

 public boolean isLikedByMember(Integer designerId, String memberId) {
  if (memberId == null || memberId.isBlank()) return false;
  return designerLikeRepository.existsByMember_MemberIdAndDesigner_DesignerId(memberId, designerId);
 }

 @Transactional
 public boolean like(Integer designerId, String memberId) {
  Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  Member member = memberRepository.findById(memberId).orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
  if (designerLikeRepository.findByMember_MemberIdAndDesigner_DesignerId(memberId, designerId).isPresent())
   return false;
  designerLikeRepository.save(DesignerLike.builder().member(member).designer(designer).build());
  designer.setLikeCount(Math.toIntExact(designerLikeRepository.countByDesigner_DesignerId(designerId)));
  return true;
 }

 @Transactional
 public boolean unlike(Integer designerId, String memberId) {
  Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  return designerLikeRepository.findByMember_MemberIdAndDesigner_DesignerId(memberId, designerId).map(like -> {
   designerLikeRepository.delete(like);
   designer.setLikeCount(Math.toIntExact(designerLikeRepository.countByDesigner_DesignerId(designerId)));
   return true;
  }).orElse(false);
 }

 @Transactional
 public Integer create(DesignerCreateRequest request) {
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  Member member = null;
  if (request.getMemberId() != null && !request.getMemberId().isBlank())
   member = memberRepository.findById(request.getMemberId()).orElseThrow(() -> new EntityNotFoundException("Member not found: " + request.getMemberId()));
  Designer designer = Designer.builder().salon(salon).member(member).name(request.getName()).profileImage(request.getProfileImage()).introduction(request.getIntroduction()).careerYears(request.getCareerYears()).build();
  return designerRepository.save(designer).getDesignerId();
 }

 @Transactional
 public void update(Integer designerId, DesignerUpdateRequest request) {
  Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  Member member = null;
  if (request.getMemberId() != null && !request.getMemberId().isBlank())
   member = memberRepository.findById(request.getMemberId()).orElseThrow(() -> new EntityNotFoundException("Member not found: " + request.getMemberId()));
  designer.setSalon(salon);
  designer.setMember(member);
  if (request.getName() != null && !request.getName().isBlank()) designer.setName(request.getName());
  designer.setProfileImage(request.getProfileImage());
  designer.setIntroduction(request.getIntroduction());
  designer.setCareerYears(request.getCareerYears());
 }

 @Transactional
 public void delete(Integer designerId) {
  designerRepository.deleteById(designerId);
 }

 private DesignerSummaryResponse toSummary(Designer d, DesignerRatingRow ratingRow) {
  return DesignerSummaryResponse.builder().designerId(d.getDesignerId()).salonId(d.getSalon().getSalonId()).salonName(d.getSalon().getName()).name(d.getName()).profileImage(d.getProfileImage()).careerYears(d.getCareerYears()).averageRating(ratingRow == null ? BigDecimal.ZERO : BigDecimal.valueOf(ratingRow.getAverageRating())).reviewCount(ratingRow == null ? 0L : ratingRow.getReviewCount()).likeCount(d.getLikeCount() == null ? 0 : d.getLikeCount()).createdAt(d.getCreatedAt()).build();
 }

 private Comparator<DesignerSummaryResponse> designerComparator(String sortBy) {
  if ("likes".equalsIgnoreCase(sortBy))
   return Comparator.comparing(DesignerSummaryResponse::getLikeCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  if ("career".equalsIgnoreCase(sortBy))
   return Comparator.comparing(DesignerSummaryResponse::getCareerYears, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  if ("reviews".equalsIgnoreCase(sortBy))
   return Comparator.comparing(DesignerSummaryResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  if ("name".equalsIgnoreCase(sortBy))
   return Comparator.comparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder()));
  if ("newest".equalsIgnoreCase(sortBy))
   return Comparator.comparing(DesignerSummaryResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getLikeCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  return Comparator.comparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getCareerYears, Comparator.nullsLast(Comparator.reverseOrder()));
 }

 private DesignerRatingRow findRatingRow(Integer designerId) {
  return designerRepository.findDesignerRatingRows().stream().filter(r -> designerId.equals(r.getDesignerId())).findFirst().orElse(null);
 }
}

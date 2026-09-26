package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.designer.dto.response.DesignerCatalogPage;
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
 private final CatalogIntegrityService catalogIntegrityService;

 public List<DesignerSummaryResponse> search(DesignerSearchRequest request) {
  DesignerSearchRequest safeRequest = request == null ? new DesignerSearchRequest() : request;
  return search(safeRequest, 0, Integer.MAX_VALUE).getContent();
 }

 public Page<DesignerSummaryResponse> search(DesignerSearchRequest request, int page, int size) {
  DesignerSearchRequest safeRequest = request == null ? new DesignerSearchRequest() : request;
  return page(loadFiltered(safeRequest), safeRequest.getSortBy(), page, size);
 }

 public DesignerCatalogPage searchCatalog(DesignerSearchRequest request, int page, int size) {
  List<DesignerSummaryResponse> filtered = loadFiltered(request == null ? new DesignerSearchRequest() : request);
  return new DesignerCatalogPage(page(filtered, null, page, size), page(filtered, "likes", page, size), page(filtered, "newest", page, size));
 }

 private List<DesignerSummaryResponse> loadFiltered(DesignerSearchRequest request) {
  List<Designer> designers = designerRepository.findAll(DesignerSpecifications.bySearch(request));
  Map<Integer, DesignerRatingRow> ratings = ratingsFor(designers.stream().map(Designer::getDesignerId).toList());
  return designers.stream().map(d -> toSummary(d, ratings.get(d.getDesignerId())))
   .filter(d -> request.getMinRating() == null || d.getAverageRating().compareTo(request.getMinRating()) >= 0)
   .filter(d -> request.getMinReviewCount() == null || d.getReviewCount() >= request.getMinReviewCount()).toList();
 }

 private Page<DesignerSummaryResponse> page(List<DesignerSummaryResponse> filtered, String sortBy, int page, int size) {
  List<DesignerSummaryResponse> sorted = filtered.stream().sorted(designerComparator(sortBy)
   .thenComparing(DesignerSummaryResponse::getDesignerId)).toList();
  int safeSize = Math.max(size, 1);
  int maxPage = sorted.isEmpty() ? 0 : (sorted.size() - 1) / safeSize;
  int safePage = Math.min(Math.max(page, 0), maxPage);
  int fromIndex = Math.min(safePage * safeSize, sorted.size());
  int toIndex = (int) Math.min((long) fromIndex + safeSize, sorted.size());
  return new PageImpl<>(sorted.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), sorted.size());
 }

 private Map<Integer, DesignerRatingRow> ratingsFor(List<Integer> designerIds) {
  if (designerIds.isEmpty()) return Map.of();
  return designerRepository.findDesignerRatingRowsByIds(designerIds).stream()
   .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));
 }

 public DesignerDetailResponse getDetail(Integer designerId, String loginMemberId) {
  Designer designer = designerRepository.findDetailByDesignerId(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  DesignerRatingRow row = ratingsFor(List.of(designerId)).get(designerId);
  List<SalonServiceSummaryResponse> services = salonServiceRepository.findBySalonSalonId(designer.getSalon().getSalonId()).stream().map(s -> SalonServiceSummaryResponse.builder().serviceId(s.getServiceId()).salonId(s.getSalon().getSalonId()).salonName(s.getSalon().getName()).name(s.getName()).price(s.getPrice()).duration(s.getDuration()).build()).toList();
  return DesignerDetailResponse.builder().designerId(designer.getDesignerId()).salonId(designer.getSalon().getSalonId()).salonName(designer.getSalon().getName()).memberId(designer.getMember() != null ? designer.getMember().getMemberId() : null).name(designer.getName()).profileImage(designer.getProfileImage()).introduction(designer.getIntroduction()).careerYears(designer.getCareerYears()).averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating())).reviewCount(row == null ? 0L : row.getReviewCount()).likeCount(designer.getLikeCount() == null ? 0 : designer.getLikeCount()).likedByCurrentUser(isLikedByMember(designerId, loginMemberId)).salonServices(services).build();
 }

 public DesignerDetailResponse getDetail(Integer designerId) {
  return getDetail(designerId, null);
 }

 public List<DesignerSummaryResponse> getLikedDesigners(String memberId) {
  List<Designer> designers = designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId)
   .stream().map(DesignerLike::getDesigner).toList();
  Map<Integer, DesignerRatingRow> ratings = ratingsFor(designers.stream().map(Designer::getDesignerId).toList());
  return designers.stream().map(designer -> toSummary(designer, ratings.get(designer.getDesignerId()))).toList();
 }

 public List<Integer> getLikedDesignerIds(String memberId) {
  return designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream().map(like -> like.getDesigner().getDesignerId()).toList();
 }

 public boolean isLikedByMember(Integer designerId, String memberId) {
  if (memberId == null || memberId.isBlank()) return false;
  return designerLikeRepository.existsByMember_MemberIdAndDesigner_DesignerId(memberId, designerId);
 }

 public boolean existsSalon(Integer salonId) {
  return salonId != null && salonRepository.existsById(salonId);
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
  Member member = catalogIntegrityService.validateDesignerMember(request.getMemberId(), null);
  Designer designer = Designer.builder().salon(salon).member(member).name(request.getName()).profileImage(request.getProfileImage()).introduction(request.getIntroduction()).careerYears(request.getCareerYears()).build();
  return designerRepository.save(designer).getDesignerId();
 }

 @Transactional
 public void update(Integer designerId, DesignerUpdateRequest request) {
  // Validate member -> designer in the same order as creation and role changes.
  Member member = catalogIntegrityService.validateDesignerMember(request.getMemberId(), designerId);
  catalogIntegrityService.assertDesignerMoveAllowed(designerId, request.getSalonId());
  Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  designer.setSalon(salon);
  designer.setMember(member);
  if (request.getName() != null && !request.getName().isBlank()) designer.setName(request.getName());
  designer.setProfileImage(request.getProfileImage());
  designer.setIntroduction(request.getIntroduction());
  designer.setCareerYears(request.getCareerYears());
 }

 @Transactional
 public void delete(Integer designerId) {
  catalogIntegrityService.assertDesignerDeletable(designerId);
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

}

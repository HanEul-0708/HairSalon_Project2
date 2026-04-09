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
        Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows()
                .stream()
                .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));

        List<DesignerSummaryResponse> filtered = designerRepository.findAll(DesignerSpecifications.bySearch(request))
                .stream()
                .map(d -> toSummary(d, ratings.get(d.getDesignerId())))
                .filter(d -> request.getMinRating() == null
                        || d.getAverageRating().compareTo(request.getMinRating()) >= 0)
                .filter(d -> request.getMinReviewCount() == null
                        || d.getReviewCount() >= request.getMinReviewCount())
                .sorted(
                        designerComparator(request.getSortBy())
                )
                .toList();

        int safeSize = Math.max(size, 1);
        int maxPage = filtered.isEmpty() ? 0 : (filtered.size() - 1) / safeSize;
        int safePage = Math.min(Math.max(page, 0), maxPage);
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        return new PageImpl<>(
                filtered.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                filtered.size()
        );
    }

    public DesignerDetailResponse getDetail(Integer designerId, String loginMemberId) {
        Designer designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));

        DesignerRatingRow row = designerRepository.findDesignerRatingRows()
                .stream()
                .filter(r -> designerId.equals(r.getDesignerId()))
                .findFirst()
                .orElse(null);

        List<SalonServiceSummaryResponse> services = salonServiceRepository
                .findBySalonSalonId(designer.getSalon().getSalonId())
                .stream()
                .map(s -> SalonServiceSummaryResponse.builder()
                        .serviceId(s.getServiceId())
                        .salonId(s.getSalon().getSalonId())
                        .salonName(s.getSalon().getName())
                        .name(s.getName())
                        .price(s.getPrice())
                        .duration(s.getDuration())
                        .build())
                .toList();

        return DesignerDetailResponse.builder()
                .designerId(designer.getDesignerId())
                .salonId(designer.getSalon().getSalonId())
                .salonName(designer.getSalon().getName())
                .memberId(designer.getMember() != null ? designer.getMember().getMemberId() : null)
                .name(designer.getName())
                .profileImage(designer.getProfileImage())
                .introduction(designer.getIntroduction())
                .careerYears(designer.getCareerYears())
                .averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating()))
                .reviewCount(row == null ? 0L : row.getReviewCount())
                .likeCount(designer.getLikeCount() == null ? 0 : designer.getLikeCount())
                .likedByCurrentUser(isLikedByMember(designerId, loginMemberId))
                .salonServices(services)
                .build();
    }

    public DesignerDetailResponse getDetail(Integer designerId) {
        return getDetail(designerId, null);
    }

    public List<DesignerSummaryResponse> getLikedDesigners(String memberId) {
        return designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(DesignerLike::getDesigner)
                .map(designer -> toSummary(designer, findRatingRow(designer.getDesignerId())))
                .toList();
    }

    public List<Integer> getLikedDesignerIds(String memberId) {
        return designerLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(like -> like.getDesigner().getDesignerId())
                .toList();
    }

    public boolean isLikedByMember(Integer designerId, String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }
        return designerLikeRepository.existsByMember_MemberIdAndDesigner_DesignerId(memberId, designerId);
    }

    @Transactional
    public boolean like(Integer designerId, String memberId) {
        Designer designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));

        if (designerLikeRepository.findByMember_MemberIdAndDesigner_DesignerId(memberId, designerId).isPresent()) {
            return false;
        }

        designerLikeRepository.save(DesignerLike.builder()
                .member(member)
                .designer(designer)
                .build());
        designer.setLikeCount(Math.toIntExact(designerLikeRepository.countByDesigner_DesignerId(designerId)));
        return true;
    }

    @Transactional
    public boolean unlike(Integer designerId, String memberId) {
        Designer designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));

        return designerLikeRepository.findByMember_MemberIdAndDesigner_DesignerId(memberId, designerId)
                .map(like -> {
                    designerLikeRepository.delete(like);
                    designer.setLikeCount(Math.toIntExact(designerLikeRepository.countByDesigner_DesignerId(designerId)));
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Integer create(DesignerCreateRequest request) {
        Salon salon = salonRepository.findById(request.getSalonId())
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

        Member member = null;
        if (request.getMemberId() != null && !request.getMemberId().isBlank()) {
            member = memberRepository.findById(request.getMemberId())
                    .orElseThrow(() -> new EntityNotFoundException("Member not found: " + request.getMemberId()));
        }

        Designer designer = Designer.builder()
                .salon(salon)
                .member(member)
                .name(request.getName())
                .profileImage(request.getProfileImage())
                .introduction(request.getIntroduction())
                .careerYears(request.getCareerYears())
                .build();

        return designerRepository.save(designer).getDesignerId();
    }

    @Transactional
    public void update(Integer designerId, DesignerUpdateRequest request) {
        Designer designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));

        Salon salon = salonRepository.findById(request.getSalonId())
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

        Member member = null;
        if (request.getMemberId() != null && !request.getMemberId().isBlank()) {
            member = memberRepository.findById(request.getMemberId())
                    .orElseThrow(() -> new EntityNotFoundException("Member not found: " + request.getMemberId()));
        }

        designer.setSalon(salon);
        designer.setMember(member);

        if (request.getName() != null && !request.getName().isBlank()) {
            designer.setName(request.getName());
        }

        designer.setProfileImage(request.getProfileImage());
        designer.setIntroduction(request.getIntroduction());
        designer.setCareerYears(request.getCareerYears());
    }

    @Transactional
    public void delete(Integer designerId) {
        designerRepository.deleteById(designerId);
    }

    private DesignerSummaryResponse toSummary(Designer d, DesignerRatingRow ratingRow) {
        return DesignerSummaryResponse.builder()
                .designerId(d.getDesignerId())
                .salonId(d.getSalon().getSalonId())
                .salonName(d.getSalon().getName())
                .name(d.getName())
                .profileImage(d.getProfileImage())
                .careerYears(d.getCareerYears())
                .averageRating(ratingRow == null ? BigDecimal.ZERO : BigDecimal.valueOf(ratingRow.getAverageRating()))
                .reviewCount(ratingRow == null ? 0L : ratingRow.getReviewCount())
                .likeCount(d.getLikeCount() == null ? 0 : d.getLikeCount())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private Comparator<DesignerSummaryResponse> designerComparator(String sortBy) {
        if ("likes".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            DesignerSummaryResponse::getLikeCount,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            DesignerSummaryResponse::getAverageRating,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
        }

        if ("career".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            DesignerSummaryResponse::getCareerYears,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            DesignerSummaryResponse::getAverageRating,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
        }

        if ("reviews".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            DesignerSummaryResponse::getReviewCount,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            DesignerSummaryResponse::getAverageRating,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
        }

        if ("name".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(DesignerSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                            DesignerSummaryResponse::getAverageRating,
                            Comparator.nullsLast(Comparator.reverseOrder()));
        }

        return Comparator.comparing(
                        DesignerSummaryResponse::getAverageRating,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        DesignerSummaryResponse::getReviewCount,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        DesignerSummaryResponse::getCareerYears,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private DesignerRatingRow findRatingRow(Integer designerId) {
        return designerRepository.findDesignerRatingRows()
                .stream()
                .filter(r -> designerId.equals(r.getDesignerId()))
                .findFirst()
                .orElse(null);
    }
}

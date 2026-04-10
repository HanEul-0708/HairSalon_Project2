package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
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

    public List<SalonSummaryResponse> search(SalonSearchRequest request) {
        return search(request, 0, Integer.MAX_VALUE).getContent();
    }

    public Page<SalonSummaryResponse> search(SalonSearchRequest request, int page, int size) {
        SalonSearchRequest safeRequest = request == null ? new SalonSearchRequest() : request;
        List<SalonSummaryResponse> responses = searchInternal(safeRequest);
        int safeSize = Math.max(size, 1);
        int maxPage = responses.isEmpty() ? 0 : (responses.size() - 1) / safeSize;
        int safePage = Math.min(Math.max(page, 0), maxPage);
        int fromIndex = Math.min(safePage * safeSize, responses.size());
        int toIndex = Math.min(fromIndex + safeSize, responses.size());

        return new PageImpl<>(
                responses.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                responses.size()
        );
    }

    private List<SalonSummaryResponse> searchInternal(SalonSearchRequest request) {
        List<Integer> keywordMatchedSalonIds = resolveKeywordMatchedSalonIds(request);
        List<SalonSummaryResponse> responses = salonRepository.findAll(
                        SalonSpecifications.bySearch(request, keywordMatchedSalonIds))
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toCollection(ArrayList::new));

        String sort = resolveSort(request);
        if ("rating".equals(sort)) {
            responses.sort(
                    Comparator.comparing(
                                    SalonSummaryResponse::getAverageRating,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(
                                    SalonSummaryResponse::getReviewCount,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
            );
        } else if ("likes".equals(sort)) {
            responses.sort(Comparator.comparing(
                    SalonSummaryResponse::getLikeCount,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            );
        } else {
            responses.sort(
                    Comparator.comparing(
                                    SalonSummaryResponse::getAverageRating,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(
                                    SalonSummaryResponse::getReviewCount,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(
                                    SalonSummaryResponse::getLikeCount,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
            );
        }

        return responses;
    }

    private List<Integer> resolveKeywordMatchedSalonIds(SalonSearchRequest request) {
        if (request == null || !request.isServiceKeywordSearchEnabled()) {
            return List.of();
        }

        String keyword = request.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return salonServiceRepository.findDistinctSalonIdsByKeyword(keyword);
    }

    private String resolveSort(SalonSearchRequest request) {
        if (request.getSort() == null || request.getSort().isBlank()) {
            return "recommended";
        }

        return switch (request.getSort().toLowerCase()) {
            case "rating", "likes", "recommended" -> request.getSort().toLowerCase();
            default -> "recommended";
        };
    }

    public SalonDetailResponse getDetail(Integer salonId) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));

        Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows()
                .stream()
                .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));

        List<DesignerSummaryResponse> designers = designerRepository.findBySalonSalonId(salonId)
                .stream()
                .map(d -> {
                    DesignerRatingRow row = ratings.get(d.getDesignerId());
                    return DesignerSummaryResponse.builder()
                            .designerId(d.getDesignerId())
                            .salonId(salonId)
                            .salonName(salon.getName())
                            .name(d.getName())
                            .profileImage(d.getProfileImage())
                            .careerYears(d.getCareerYears())
                            .averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating()))
                            .reviewCount(row == null ? 0L : row.getReviewCount())
                            .build();
                })
                .toList();

        List<SalonServiceSummaryResponse> services = salonServiceRepository.findBySalonSalonId(salonId)
                .stream()
                .map(s -> SalonServiceSummaryResponse.builder()
                        .serviceId(s.getServiceId())
                        .salonId(salonId)
                        .salonName(salon.getName())
                        .name(s.getName())
                        .price(s.getPrice())
                        .duration(s.getDuration())
                        .build())
                .toList();

        return SalonDetailResponse.builder()
                .salonId(salon.getSalonId())
                .externalId(salon.getExternalId())
                .sourceType(salon.getSourceType())
                .name(salon.getName())
                .address(salon.getAddress())
                .roadAddress(salon.getRoadAddress())
                .phone(salon.getPhone())
                .description(salon.getDescription())
                .imageUrl(salon.getImageUrl())
                .placeUrl(salon.getPlaceUrl())
                .reservable(salon.getReservable())
                .averageRating(salon.getAverageRating())
                .reviewCount(salon.getReviewCount())
                .likeCount(salon.getLikeCount())
                .designers(designers)
                .services(services)
                .build();
    }

    public List<SalonSummaryResponse> getLikedSalons(String memberId) {
        return salonLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(SalonLike::getSalon)
                .map(this::toSummary)
                .toList();
    }

    public List<Integer> getLikedSalonIds(String memberId) {
        return salonLikeRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(like -> like.getSalon().getSalonId())
                .toList();
    }

    public boolean isLikedByMember(Integer salonId, String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }

        return salonLikeRepository.existsByMember_MemberIdAndSalon_SalonId(memberId, salonId);
    }

    public List<SalonRankResponse> recommendedTop3() {
        List<Salon> salons = salonRepository.findRecommendedSalons(PageRequest.of(0, 3));
        List<SalonRankResponse> result = new ArrayList<>();

        for (int i = 0; i < salons.size(); i++) {
            Salon salon = salons.get(i);
            result.add(SalonRankResponse.builder()
                    .rank(i + 1)
                    .salonId(salon.getSalonId())
                    .salonName(salon.getName())
                    .averageRating(salon.getAverageRating())
                    .reviewCount(salon.getReviewCount())
                    .likeCount(salon.getLikeCount())
                    .build());
        }

        return result;
    }

    @Transactional
    public Integer create(SalonCreateRequest request) {
        Salon salon = Salon.builder()
                .externalId(request.getExternalId())
                .sourceType(request.getSourceType())
                .name(request.getName())
                .address(request.getAddress())
                .roadAddress(request.getRoadAddress())
                .phone(request.getPhone())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .placeUrl(request.getPlaceUrl())
                .reservable(request.getReservable() != null ? request.getReservable() : Boolean.TRUE)
                .build();

        return salonRepository.save(salon).getSalonId();
    }

    @Transactional
    public void update(Integer salonId, SalonUpdateRequest request) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));

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

    @Transactional
    public boolean like(Integer salonId, String memberId) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));

        if (salonLikeRepository.findByMember_MemberIdAndSalon_SalonId(memberId, salonId).isPresent()) {
            return false;
        }

        salonLikeRepository.save(
                SalonLike.builder()
                        .member(member)
                        .salon(salon)
                        .build()
        );

        salon.setLikeCount(Math.toIntExact(salonLikeRepository.countBySalon_SalonId(salonId)));
        return true;
    }

    @Transactional
    public boolean unlike(Integer salonId, String memberId) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));

        return salonLikeRepository.findByMember_MemberIdAndSalon_SalonId(memberId, salonId)
                .map(like -> {
                    salonLikeRepository.delete(like);
                    salon.setLikeCount(Math.toIntExact(salonLikeRepository.countBySalon_SalonId(salonId)));
                    return true;
                })
                .orElse(false);
    }

    private SalonSummaryResponse toSummary(Salon salon) {
        return SalonSummaryResponse.builder()
                .salonId(salon.getSalonId())
                .name(salon.getName())
                .address(salon.getAddress())
                .roadAddress(salon.getRoadAddress())
                .phone(salon.getPhone())
                .imageUrl(salon.getImageUrl())
                .averageRating(salon.getAverageRating())
                .reviewCount(salon.getReviewCount())
                .likeCount(salon.getLikeCount())
                .reservable(salon.getReservable())
                .createdAt(salon.getCreatedAt())
                .build();
    }
}

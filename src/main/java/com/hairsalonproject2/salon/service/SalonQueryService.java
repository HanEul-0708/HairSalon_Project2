package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.support.GeoUtils;
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

    public List<SalonSummaryResponse> search(SalonSearchRequest request) {
        List<Integer> keywordMatchedSalonIds = resolveKeywordMatchedSalonIds(request.getKeyword());
        List<Salon> salons = salonRepository.findAll(SalonSpecifications.bySearch(request, keywordMatchedSalonIds));
        List<SalonSummaryResponse> responses = new ArrayList<>();
        boolean requiresDistanceFiltering = request.getLatitude() != null
                && request.getLongitude() != null
                && (request.getRadiusKm() != null || "distance".equalsIgnoreCase(resolveSort(request)));

        for (Salon salon : salons) {
            Double distance = resolveDistanceKm(request, salon);
            if (requiresDistanceFiltering && distance == null) {
                continue;
            }
            if (request.getRadiusKm() != null && distance != null && distance > request.getRadiusKm()) {
                continue;
            }
            responses.add(toSummary(salon, distance));
        }

        String sort = resolveSort(request);
        if ("distance".equalsIgnoreCase(sort)) {
            responses.sort(Comparator.comparing(r -> r.getDistanceKm() == null ? Double.MAX_VALUE : r.getDistanceKm()));
        } else if ("rating".equalsIgnoreCase(sort)) {
            responses.sort(
                    Comparator.comparing(
                                    SalonSummaryResponse::getAverageRating,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(
                                    SalonSummaryResponse::getReviewCount,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
            );
        } else if ("likes".equalsIgnoreCase(sort)) {
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

    private List<Integer> resolveKeywordMatchedSalonIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return salonServiceRepository.findDistinctSalonIdsByKeyword(keyword);
    }

    private String resolveSort(SalonSearchRequest request) {
        if (request.getSort() == null || request.getSort().isBlank()) {
            return "recommended";
        }
        return request.getSort();
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
                    var row = ratings.get(d.getDesignerId());
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
                .latitude(salon.getLatitude())
                .longitude(salon.getLongitude())
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

    public List<SalonRankResponse> recommendedTop3() {
        List<Salon> salons = salonRepository.findRecommendedSalons(PageRequest.of(0, 3));
        List<SalonRankResponse> result = new ArrayList<>();

        for (int i = 0; i < salons.size(); i++) {
            Salon s = salons.get(i);
            result.add(SalonRankResponse.builder()
                    .rank(i + 1)
                    .salonId(s.getSalonId())
                    .salonName(s.getName())
                    .averageRating(s.getAverageRating())
                    .reviewCount(s.getReviewCount())
                    .likeCount(s.getLikeCount())
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
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
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
        salon.setLatitude(request.getLatitude());
        salon.setLongitude(request.getLongitude());
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

    private SalonSummaryResponse toSummary(Salon salon, Double distanceKm) {
        return SalonSummaryResponse.builder()
                .salonId(salon.getSalonId())
                .name(salon.getName())
                .address(salon.getAddress())
                .phone(salon.getPhone())
                .imageUrl(salon.getImageUrl())
                .latitude(salon.getLatitude())
                .longitude(salon.getLongitude())
                .averageRating(salon.getAverageRating())
                .reviewCount(salon.getReviewCount())
                .likeCount(salon.getLikeCount())
                .reservable(salon.getReservable())
                .distanceKm(distanceKm)
                .build();
    }

    private Double resolveDistanceKm(SalonSearchRequest request, Salon salon) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            return null;
        }
        if (salon.getLatitude() == null || salon.getLongitude() == null) {
            return null;
        }

        double distance = GeoUtils.distanceKm(
                request.getLatitude().doubleValue(),
                request.getLongitude().doubleValue(),
                salon.getLatitude().doubleValue(),
                salon.getLongitude().doubleValue()
        );

        return BigDecimal.valueOf(distance)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

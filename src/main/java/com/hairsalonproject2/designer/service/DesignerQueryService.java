package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerCreateRequest;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.designer.dto.response.DesignerDetailResponse;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
    private final SalonRepository salonRepository;
    private final SalonServiceRepository salonServiceRepository;

    public List<DesignerSummaryResponse> search(DesignerSearchRequest request) {
        Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows().stream().collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));

        return designerRepository.findAll(DesignerSpecifications.bySearch(request)).stream().map(d -> toSummary(d, ratings.get(d.getDesignerId()))).filter(d -> request.getMinRating() == null || d.getAverageRating().compareTo(request.getMinRating()) >= 0).sorted(Comparator.comparing(DesignerSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(DesignerSummaryResponse::getCareerYears, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    public DesignerDetailResponse getDetail(Integer designerId) {
        Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));

        DesignerRatingRow row = designerRepository.findDesignerRatingRows().stream().filter(r -> designerId.equals(r.getDesignerId())).findFirst().orElse(null);

        List<SalonServiceSummaryResponse> services = salonServiceRepository.findBySalonSalonId(designer.getSalon().getSalonId()).stream().map(s -> SalonServiceSummaryResponse.builder().serviceId(s.getServiceId()).salonId(s.getSalon().getSalonId()).salonName(s.getSalon().getName()).name(s.getName()).price(s.getPrice()).duration(s.getDuration()).build()).toList();

        return DesignerDetailResponse.builder().designerId(designer.getDesignerId()).salonId(designer.getSalon().getSalonId()).salonName(designer.getSalon().getName()).memberId(designer.getMemberId()).name(designer.getName()).profileImage(designer.getProfileImage()).introduction(designer.getIntroduction()).careerYears(designer.getCareerYears()).averageRating(row == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getAverageRating())).reviewCount(row == null ? 0L : row.getReviewCount()).salonServices(services).build();
    }

    @Transactional
    public Integer create(DesignerCreateRequest request) {
        Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

        Designer designer = Designer.builder().salon(salon).memberId(request.getMemberId()).name(request.getName()).profileImage(request.getProfileImage()).introduction(request.getIntroduction()).careerYears(request.getCareerYears()).build();

        return designerRepository.save(designer).getDesignerId();
    }

    @Transactional
    public void update(Integer designerId, DesignerUpdateRequest request) {
        Designer designer = designerRepository.findById(designerId).orElseThrow(() -> new EntityNotFoundException("Designer not found: " + designerId));
        Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

        designer.setSalon(salon);
        designer.setMemberId(request.getMemberId());
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
        return DesignerSummaryResponse.builder().designerId(d.getDesignerId()).salonId(d.getSalon().getSalonId()).salonName(d.getSalon().getName()).name(d.getName()).profileImage(d.getProfileImage()).careerYears(d.getCareerYears()).averageRating(ratingRow == null ? BigDecimal.ZERO : BigDecimal.valueOf(ratingRow.getAverageRating())).reviewCount(ratingRow == null ? 0L : ratingRow.getReviewCount()).build();
    }
}
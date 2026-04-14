package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SalonRatingSyncService {

    private final SalonRepository salonRepository;
    private final DesignerRepository designerRepository;

    public void syncSalonStats(Integer salonId) {
        syncSalonStats(List.of(salonId));
    }

    public void syncSalonStats(List<Integer> salonIds) {
        if (salonIds == null || salonIds.isEmpty()) {
            return;
        }

        Map<Integer, DesignerRatingRow> ratingRows = designerRepository.findDesignerRatingRows().stream()
                .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));

        new LinkedHashSet<>(salonIds).forEach(salonId -> syncSingleSalon(salonId, ratingRows));
    }

    private void syncSingleSalon(Integer salonId, Map<Integer, DesignerRatingRow> ratingRows) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + salonId));

        List<DesignerRatingRow> ratedDesigners = designerRepository.findBySalonSalonId(salonId).stream()
                .map(Designer::getDesignerId)
                .map(ratingRows::get)
                .filter(row -> row != null && row.getReviewCount() != null && row.getReviewCount() > 0)
                .toList();

        int reviewCount = ratedDesigners.stream()
                .map(DesignerRatingRow::getReviewCount)
                .mapToInt(Long::intValue)
                .sum();

        BigDecimal averageRating = ratedDesigners.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(ratedDesigners.stream()
                        .map(DesignerRatingRow::getAverageRating)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0))
                .setScale(2, RoundingMode.HALF_UP);

        salon.setReviewCount(reviewCount);
        salon.setAverageRating(averageRating);
    }
}

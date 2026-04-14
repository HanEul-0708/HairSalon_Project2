package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonRatingSyncServiceTest {

    @Mock
    private SalonRepository salonRepository;

    @Mock
    private DesignerRepository designerRepository;

    @InjectMocks
    private SalonRatingSyncService salonRatingSyncService;

    @Test
    void syncSalonStatsUsesAverageOfDesignerAveragesWithinSalon() {
        Salon salon = Salon.builder()
                .salonId(1)
                .name("Salon")
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build();

        Designer first = Designer.builder().designerId(10).salon(salon).name("A").build();
        Designer second = Designer.builder().designerId(20).salon(salon).name("B").build();
        Designer third = Designer.builder().designerId(30).salon(salon).name("C").build();

        when(salonRepository.findById(1)).thenReturn(Optional.of(salon));
        when(designerRepository.findBySalonSalonId(1)).thenReturn(List.of(first, second, third));
        when(designerRepository.findDesignerRatingRows()).thenReturn(List.of(
                ratingRow(10, 4.8, 12L),
                ratingRow(20, 3.6, 8L),
                ratingRow(30, 0.0, 0L)
        ));

        salonRatingSyncService.syncSalonStats(1);

        assertThat(salon.getAverageRating()).isEqualByComparingTo("4.20");
        assertThat(salon.getReviewCount()).isEqualTo(20);
    }

    private DesignerRatingRow ratingRow(Integer designerId, Double averageRating, Long reviewCount) {
        return new DesignerRatingRow() {
            @Override
            public Integer getDesignerId() {
                return designerId;
            }

            @Override
            public Double getAverageRating() {
                return averageRating;
            }

            @Override
            public Long getReviewCount() {
                return reviewCount;
            }
        };
    }
}

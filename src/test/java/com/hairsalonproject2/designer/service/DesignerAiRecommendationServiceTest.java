package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.llm.DesignerAiLlmRecommendation;
import com.hairsalonproject2.designer.llm.DesignerAiLlmResult;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignerAiRecommendationServiceTest {
    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private DesignerAiClient designerAiClient;

    private DesignerAiRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new DesignerAiRecommendationService(
                designerRepository,
                salonServiceRepository,
                reviewRepository,
                designerAiClient
        );
    }

    @Test
    void recommendsDesignerByStyleKeywordWithoutLlm() {
        Salon colorSalon = salon(1, "Color Lab");
        Salon cutSalon = salon(2, "Cut Studio");
        Designer colorDesigner = designer(1, colorSalon, "Color Kim", "톤다운 염색과 퍼스널 컬러 상담 전문", 8, DesignerSpecialty.COLOR, 24);
        Designer cutDesigner = designer(2, cutSalon, "Cut Lee", "남성 커트와 다운펌 전문", 5, DesignerSpecialty.CUT, 7);

        when(designerRepository.findAll()).thenReturn(List.of(cutDesigner, colorDesigner));
        when(designerRepository.findDesignerRatingRows()).thenReturn(List.of(
                ratingRow(1, 4.7, 18L),
                ratingRow(2, 4.9, 12L)
        ));
        when(salonServiceRepository.findBySalonSalonId(1)).thenReturn(List.of(
                service(colorSalon, "프리미엄 염색", "톤다운, 톤업, 브릿지 컬러")
        ));
        when(salonServiceRepository.findBySalonSalonId(2)).thenReturn(List.of(
                service(cutSalon, "남성 커트", "깔끔한 비즈니스 커트")
        ));
        when(reviewRepository.findByDesigner_DesignerId(anyInt())).thenReturn(List.of());
        when(designerAiClient.recommend(anyString(), anyList(), eq(3))).thenReturn(Optional.empty());

        DesignerAiRecommendationResponse response = service.recommend("톤다운 염색 잘하는 디자이너", 3);

        assertThat(response.isLlmUsed()).isFalse();
        assertThat(response.getSource()).isEqualTo("LOCAL_RULES");
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations().getFirst().getDesignerId()).isEqualTo(1);
        assertThat(response.getRecommendations().getFirst().getTags()).contains("염색");
    }

    @Test
    void appliesLlmOrderingAndReasonWhenAvailable() {
        Salon firstSalon = salon(1, "First Salon");
        Salon secondSalon = salon(2, "Second Salon");
        Designer firstDesigner = designer(1, firstSalon, "First Designer", "커트 전문", 6, DesignerSpecialty.CUT, 4);
        Designer secondDesigner = designer(2, secondSalon, "Second Designer", "스타일링과 드라이 전문", 4, DesignerSpecialty.STYLING, 15);

        when(designerRepository.findAll()).thenReturn(List.of(firstDesigner, secondDesigner));
        when(designerRepository.findDesignerRatingRows()).thenReturn(List.of(
                ratingRow(1, 4.8, 10L),
                ratingRow(2, 4.5, 8L)
        ));
        when(salonServiceRepository.findBySalonSalonId(1)).thenReturn(List.of(service(firstSalon, "커트", "레이어드 커트")));
        when(salonServiceRepository.findBySalonSalonId(2)).thenReturn(List.of(service(secondSalon, "드라이", "행사용 스타일링")));
        when(reviewRepository.findByDesigner_DesignerId(anyInt())).thenReturn(List.of());
        when(designerAiClient.recommend(anyString(), anyList(), eq(2))).thenReturn(Optional.of(
                new DesignerAiLlmResult(
                        "LLM이 스타일링 요청에 맞춰 추천했습니다.",
                        List.of(new DesignerAiLlmRecommendation(2, "행사용 스타일링 요청과 가장 잘 맞습니다.", List.of("스타일링", "행사")))
                )
        ));

        DesignerAiRecommendationResponse response = service.recommend("행사 전에 드라이 잘하는 디자이너", 2);

        assertThat(response.isLlmUsed()).isTrue();
        assertThat(response.getSource()).isEqualTo("LLM");
        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().getFirst().getDesignerId()).isEqualTo(2);
        assertThat(response.getRecommendations().getFirst().getReason()).isEqualTo("행사용 스타일링 요청과 가장 잘 맞습니다.");
    }

    private Salon salon(Integer salonId, String name) {
        return Salon.builder()
                .salonId(salonId)
                .name(name)
                .address("Seoul")
                .reservable(true)
                .build();
    }

    private Designer designer(Integer designerId,
                              Salon salon,
                              String name,
                              String introduction,
                              Integer careerYears,
                              DesignerSpecialty specialty,
                              Integer likeCount) {
        return Designer.builder()
                .designerId(designerId)
                .salon(salon)
                .name(name)
                .introduction(introduction)
                .careerYears(careerYears)
                .specialty(specialty)
                .likeCount(likeCount)
                .build();
    }

    private SalonService service(Salon salon, String name, String description) {
        return SalonService.builder()
                .salon(salon)
                .name(name)
                .description(description)
                .price(50000)
                .duration(60)
                .build();
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

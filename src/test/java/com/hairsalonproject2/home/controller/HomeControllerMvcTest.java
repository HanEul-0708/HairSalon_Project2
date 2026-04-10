package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.salon.dto.response.SalonRankResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private SalonQueryService salonQueryService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void homeShowsFirstRegionalHighlightAndRotationData() throws Exception {
        when(salonQueryService.getAddressOptions()).thenReturn(List.of(
                "부산광역시 수영구 광안동",
                "부산광역시 부산진구 부전동"
        ));

        when(salonQueryService.recommendedTop3(eq("부산광역시"), eq("부산진구"), isNull()))
                .thenReturn(List.of(sampleSalon(1, "부전 살롱")));
        when(salonQueryService.recommendedTop3(eq("부산광역시"), eq("수영구"), isNull()))
                .thenReturn(List.of(sampleSalon(2, "광안 살롱")));

        when(reviewService.getTop3Designers(eq("부산광역시"), eq("부산진구"), isNull()))
                .thenReturn(List.of(sampleDesigner(10, "민지")));
        when(reviewService.getTop3Designers(eq("부산광역시"), eq("수영구"), isNull()))
                .thenReturn(List.of(sampleDesigner(20, "수아")));

        when(reviewService.getRecentReviews(eq("부산광역시"), eq("부산진구"), isNull()))
                .thenReturn(List.of(sampleReview("김민수", "부전 살롱")));
        when(reviewService.getRecentReviews(eq("부산광역시"), eq("수영구"), isNull()))
                .thenReturn(List.of(sampleReview("박서연", "광안 살롱")));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("currentRegionLabel", "부산광역시 부산진구"))
                .andExpect(model().attributeExists("regionalHighlights", "topSalons", "topDesigners", "recentReviews"));

        verify(salonQueryService).recommendedTop3("부산광역시", "부산진구", null);
        verify(salonQueryService).recommendedTop3("부산광역시", "수영구", null);
        verify(reviewService).getTop3Designers("부산광역시", "부산진구", null);
        verify(reviewService).getRecentReviews("부산광역시", "수영구", null);
        verify(salonQueryService, never()).recommendedTop3();
    }

    @Test
    void homeFallsBackToNationwideWhenRegionAddressesAreMissing() throws Exception {
        when(salonQueryService.getAddressOptions()).thenReturn(List.of());
        when(salonQueryService.recommendedTop3()).thenReturn(List.of(sampleSalon(99, "전국 살롱")));
        when(reviewService.getTop3Designers()).thenReturn(List.of(sampleDesigner(88, "대표 디자이너")));
        when(reviewService.getRecentReviews()).thenReturn(List.of(sampleReview("홍길동", "전국 살롱")));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("currentRegionLabel", "전국"))
                .andExpect(model().attributeExists("regionalHighlights", "topSalons", "topDesigners", "recentReviews"));

        verify(salonQueryService).recommendedTop3();
        verify(reviewService).getTop3Designers();
        verify(reviewService).getRecentReviews();
        verify(salonQueryService, never()).recommendedTop3(anyString(), anyString(), isNull());
    }

    private SalonRankResponse sampleSalon(int salonId, String salonName) {
        return SalonRankResponse.builder()
                .rank(1)
                .salonId(salonId)
                .salonName(salonName)
                .address("부산광역시 부산진구")
                .averageRating(BigDecimal.valueOf(4.9))
                .reviewCount(12)
                .likeCount(4)
                .build();
    }

    private DesignerRankingResponse sampleDesigner(int designerId, String designerName) {
        return new DesignerRankingResponse(
                designerId,
                designerName,
                "살롱 이름",
                7,
                4.8,
                9L
        );
    }

    private ReviewResponse sampleReview(String memberName, String salonName) {
        return new ReviewResponse(
                1,
                1,
                LocalDate.of(2026, 4, 10),
                LocalTime.of(13, 0),
                "user01",
                memberName,
                10,
                "민지",
                1,
                salonName,
                "부산광역시 부산진구",
                "컷",
                (byte) 5,
                "만족했습니다.",
                null,
                null,
                LocalDateTime.of(2026, 4, 10, 14, 0),
                null,
                0,
                false
        );
    }
}

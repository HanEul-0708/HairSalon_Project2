package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.home.dto.HomeReviewCardResponse;
import com.hairsalonproject2.home.dto.RegionalHighlightResponse;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

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
    void homePageRenders() throws Exception {
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("부산광역시 수영구 광안동"));
        when(salonQueryService.recommendedTop3("부산광역시", "수영구", null)).thenReturn(List.of(
                SalonRankResponse.builder()
                        .rank(1)
                        .salonId(1)
                        .salonName("광안 살롱")
                        .address("부산광역시 수영구 광안동")
                        .averageRating(BigDecimal.valueOf(4.9))
                        .reviewCount(18)
                        .likeCount(7)
                        .build()
        ));
        when(reviewService.getTop3Designers("부산광역시", "수영구", null)).thenReturn(List.of(
                new DesignerRankingResponse(1, "민지", "광안 살롱", 7, 4.8, 11L)
        ));
        when(reviewService.getRecentReviews("부산광역시", "수영구", null)).thenReturn(List.of(
                new com.hairsalonproject2.review.dto.ReviewResponse(
                        1, 1, null, null, "user1", "홍길동", 1, "민지", 1, "광안 살롱", "부산광역시 수영구 광안동",
                        "커트", (byte) 5, "좋았어요", null, null, java.time.LocalDateTime.now(), null, 0, false
                )
        ));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }
}

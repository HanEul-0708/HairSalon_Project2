package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.salon.dto.response.SalonRankResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class HomeControllerWebMvcTest {

    @Mock
    private SalonQueryService salonQueryService;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private HomeController homeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(salonQueryService.recommendedTop3()).thenReturn(List.of(
                SalonRankResponse.builder()
                        .rank(1)
                        .salonId(10)
                        .salonName("추천 살롱")
                        .address("서울 강남구")
                        .averageRating(BigDecimal.valueOf(4.8))
                        .reviewCount(25)
                        .likeCount(12)
                        .build()
        ));
        when(reviewService.getTop3Designers()).thenReturn(List.of(
                new DesignerRankingResponse(
                        20,
                        "한 디자이너",
                        "추천 살롱",
                        8,
                        4.9,
                        13L
                )
        ));
        when(reviewService.getRecentReviews()).thenReturn(List.of(
                new ReviewResponse(
                        1,
                        100,
                        LocalDate.of(2026, 4, 10),
                        LocalTime.of(14, 0),
                        "member1",
                        "고객1",
                        20,
                        "한 디자이너",
                        "추천 살롱",
                        "컷",
                        (byte) 5,
                        "좋았습니다.",
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 10, 10, 0),
                        null,
                        3,
                        false
                )
        ));

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(homeController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void homeShowsDynamicSalonAndDesignerSections() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("topSalons"))
                .andExpect(model().attributeExists("topDesigners"));
    }
}

package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
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
    private DesignerQueryService designerQueryService;

    @InjectMocks
    private HomeController homeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(salonQueryService.search(any())).thenReturn(List.of(
                SalonSummaryResponse.builder()
                        .salonId(10)
                        .name("추천 살롱")
                        .address("서울 강남구")
                        .averageRating(BigDecimal.valueOf(4.8))
                        .reviewCount(25)
                        .build()
        ));
        when(designerQueryService.search(any())).thenReturn(List.of(
                DesignerSummaryResponse.builder()
                        .designerId(20)
                        .salonId(10)
                        .salonName("추천 살롱")
                        .name("한 디자이너")
                        .careerYears(8)
                        .averageRating(BigDecimal.valueOf(4.9))
                        .reviewCount(13L)
                        .build()
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

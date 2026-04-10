package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(GlobalSearchController.class)
@Import(SecurityConfig.class)
class GlobalSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalonQueryService salonQueryService;

    @MockitoBean
    private DesignerQueryService designerQueryService;

    @MockitoBean
    private SalonServiceQueryService salonServiceQueryService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void searchPageShowsIntegratedResults() throws Exception {
        when(salonQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of(
                SalonSummaryResponse.builder()
                        .salonId(1)
                        .name("준오헤어")
                        .address("서울 강남구")
                        .averageRating(BigDecimal.valueOf(4.8))
                        .reviewCount(12)
                        .build()
        )));
        when(designerQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of(
                DesignerSummaryResponse.builder()
                        .designerId(2)
                        .salonId(1)
                        .salonName("준오헤어")
                        .name("민지 디자이너")
                        .careerYears(7)
                        .averageRating(BigDecimal.valueOf(4.9))
                        .reviewCount(15L)
                        .createdAt(LocalDateTime.of(2026, 4, 10, 10, 0))
                        .build()
        )));
        when(salonServiceQueryService.list(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of(
                SalonServiceSummaryResponse.builder()
                        .serviceId(3)
                        .salonId(1)
                        .salonName("준오헤어")
                        .name("레이어드컷")
                        .price(50000)
                        .duration(60)
                        .averageRating(4)
                        .build()
        )));

        mockMvc.perform(get("/search").param("keyword", "준오"))
                .andExpect(status().isOk())
                .andExpect(view().name("search/results"))
                .andExpect(model().attribute("keyword", "준오"))
                .andExpect(model().attribute("salonCount", 1L))
                .andExpect(model().attribute("designerCount", 1L))
                .andExpect(model().attribute("serviceCount", 1L))
                .andExpect(model().attribute("hasAnyResult", true));
    }

    @Test
    void blankKeywordRedirectsHome() throws Exception {
        mockMvc.perform(get("/search").param("keyword", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }
}

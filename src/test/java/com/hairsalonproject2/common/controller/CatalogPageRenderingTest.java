package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.controller.DesignerController;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.controller.SalonController;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonMapService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.controller.ServiceController;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({SalonController.class, DesignerController.class, ServiceController.class})
@Import(SecurityConfig.class)
class CatalogPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalonQueryService salonQueryService;

    @MockitoBean
    private ExternalSalonSyncService externalSalonSyncService;

    @MockitoBean
    private SalonMapService salonMapService;

    @MockitoBean
    private DesignerQueryService designerQueryService;

    @MockitoBean
    private SalonServiceQueryService salonServiceQueryService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void salonListTemplateRenders() throws Exception {
        when(salonQueryService.search(any(), eq(0), eq(9))).thenReturn(new PageImpl<>(List.of()));
        when(salonQueryService.getCityOptions()).thenReturn(List.of("서울특별시", "경기도"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("서울특별시 강남구 역삼동"));

        mockMvc.perform(get("/salons").param("searched", "true").param("keyword", "준오헤어"))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/list"));
    }

    @Test
    void designerListTemplateRenders() throws Exception {
        when(designerQueryService.search(any(), eq(0), eq(9))).thenReturn(new PageImpl<>(List.of()));
        when(salonQueryService.getCityOptions()).thenReturn(List.of("서울특별시", "경기도"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("서울특별시 강남구 역삼동"));

        mockMvc.perform(get("/designers").param("searched", "true").param("keyword", "민지"))
                .andExpect(status().isOk())
                .andExpect(view().name("designer/list"));
    }

    @Test
    void serviceListTemplateRenders() throws Exception {
        when(salonServiceQueryService.list(any(), eq(0), eq(8))).thenReturn(new PageImpl<>(List.of()));
        when(salonQueryService.getCityOptions()).thenReturn(List.of("서울특별시", "경기도"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("서울특별시 강남구 역삼동"));

        mockMvc.perform(get("/salon-services").param("searched", "true").param("keyword", "커트"))
                .andExpect(status().isOk())
                .andExpect(view().name("service/list"));
    }

    @Test
    void serviceCompareDoesNotQueryWithoutFilter() throws Exception {
        when(salonQueryService.getCityOptions()).thenReturn(List.of("서울특별시", "경기도"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("서울특별시 강남구 역삼동"));
        when(salonServiceQueryService.getServiceNameOptions()).thenReturn(List.of("펌", "커트"));

        mockMvc.perform(get("/salon-services/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("service/compare"));

        verify(salonServiceQueryService, never()).compare(any(), any(), any(), any());
    }

    @Test
    void serviceCompareQueriesWithFilter() throws Exception {
        when(salonServiceQueryService.compare(eq("펌"), eq("부산"), eq("수영구"), any()))
                .thenReturn(List.of());
        when(salonQueryService.getCityOptions()).thenReturn(List.of("부산"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of("수영구"));
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("부산 수영구 광안동"));
        when(salonServiceQueryService.getServiceNameOptions()).thenReturn(List.of("펌", "커트"));

        mockMvc.perform(get("/salon-services/compare")
                        .param("serviceName", "펌")
                        .param("city", "부산")
                        .param("district", "수영구"))
                .andExpect(status().isOk())
                .andExpect(view().name("service/compare"));

        verify(salonServiceQueryService).compare(eq("펌"), eq("부산"), eq("수영구"), any());
    }
}

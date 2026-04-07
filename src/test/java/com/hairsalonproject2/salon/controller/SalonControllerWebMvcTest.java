package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.admin.controller.AdminDesignerController;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.controller.ServiceController;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalonControllerWebMvcTest {

    @Mock
    private SalonQueryService salonQueryService;

    @Mock
    private ExternalSalonSyncService externalSalonSyncService;

    @Mock
    private KakaoLocalSearchClient kakaoLocalSearchClient;

    @Mock
    private SalonServiceQueryService salonServiceQueryService;

    @Mock
    private DesignerQueryService designerQueryService;

    @InjectMocks
    private SalonController salonController;

    @InjectMocks
    private ServiceController serviceController;

    @InjectMocks
    private AdminDesignerController adminDesignerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(salonQueryService.search(any())).thenReturn(List.of(
                SalonSummaryResponse.builder()
                        .salonId(1)
                        .name("테스트 살롱")
                        .address("서울 강남구")
                        .latitude(BigDecimal.valueOf(37.4979))
                        .longitude(BigDecimal.valueOf(127.0276))
                        .reservable(true)
                        .build()
        ));
        when(salonServiceQueryService.list(any())).thenReturn(List.of());
        when(kakaoLocalSearchClient.isConfigured()).thenReturn(false);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(salonController, serviceController, adminDesignerController)
                .setControllerAdvice(new BDomainPageExceptionHandler())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void anonymousCannotOpenSalonCreateForm() throws Exception {
        mockMvc.perform(get("/salons/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/login"));
    }

    @Test
    void salonCreateFormOpensForAdmin() throws Exception {
        mockMvc.perform(get("/salons/new")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/form"));
    }

    @Test
    void serviceCreateFormOpensForDesigner() throws Exception {
        mockMvc.perform(get("/salon-services/new")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "designer",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_DESIGNER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(view().name("service/form"));
    }

    @Test
    void adminDesignerCreateFormUsesDesignerTemplate() throws Exception {
        mockMvc.perform(get("/admin/designers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("designer/form"));
    }

    @Test
    void missingSalonReturnsNotFoundStatus() throws Exception {
        when(salonQueryService.getDetail(999999)).thenThrow(new EntityNotFoundException("missing"));

        mockMvc.perform(get("/salons/999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/common-error"));
    }

    @Test
    void priceCompareRouteRedirectsToSalonServiceCompare() throws Exception {
        mockMvc.perform(get("/salons/price-compare")
                        .param("serviceName", "cut")
                        .param("region", "seoul"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salon-services/compare?serviceName=cut&region=seoul"));
    }

    @Test
    void salonMapPageLoads() throws Exception {
        mockMvc.perform(get("/salons/map"))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/map"))
                .andExpect(model().attributeExists("salons"))
                .andExpect(model().attributeExists("mapCenterLatitude"))
                .andExpect(model().attributeExists("mapCenterLongitude"));
    }

    @Test
    void kakaoSearchPageShowsGracefulMessageWhenKeyMissing() throws Exception {
        mockMvc.perform(get("/salons/kakao-search").param("keyword", "컷"))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/list"))
                .andExpect(model().attribute("message", "카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다."));
    }
}

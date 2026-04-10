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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

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
                        .name("Test Salon")
                        .address("Seoul Gangnam")
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
    void salonListPageLoadsWithMergedFeatures() throws Exception {
        mockMvc.perform(get("/salons"))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/list"))
                .andExpect(model().attributeExists("salons"))
                .andExpect(model().attributeExists("search"))
                .andExpect(model().attributeExists("branchMarkers"))
                .andExpect(model().attribute("pageTitle", "살롱 통합 탐색"))
                .andExpect(model().attribute("activePreset", "all"))
                .andExpect(model().attribute("branchView", true))
                .andExpect(model().attribute("showKakaoSyncAction", true))
                .andExpect(model().attribute("searchAction", "/salons"))
                .andExpect(model().attribute("primaryKeywordPlaceholder", "이름, 주소, 설명"))
                .andExpect(model().attribute("branchMapStatus", "카카오 REST API 키가 설정되지 않아 지도를 표시할 수 없습니다."))
                .andExpect(model().attribute("kakaoResultsMessage", "검색어를 입력하면 내부 살롱과 카카오 외부 결과를 함께 비교할 수 있습니다."));
    }

    @Test
    void salonSearchAliasRedirectsToUnifiedSalonPage() throws Exception {
        mockMvc.perform(get("/salons/search").param("keyword", "cut"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?keyword=cut"));
    }

    @Test
    void topRatedRouteRedirectsToSalonPreset() throws Exception {
        mockMvc.perform(get("/salons/top-rated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?preset=top-rated&minRating=4.0&sort=rating"));
    }

    @Test
    void recommendRouteRedirectsToSalonPreset() throws Exception {
        mockMvc.perform(get("/salons/recommend-by-service").param("styleKeyword", "perm"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?keyword=perm&preset=recommend-by-service&sort=rating"));
    }

    @Test
    void branchesViewRedirectsToCanonicalSalonPage() throws Exception {
        mockMvc.perform(get("/salons").param("view", "branches").param("keyword", "cut"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?keyword=cut"));
    }

    @Test
    void kakaoViewRedirectsToCanonicalSalonPage() throws Exception {
        mockMvc.perform(get("/salons").param("view", "kakao").param("keyword", "cut"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?keyword=cut"));
    }

    @Test
    void legacyBranchesRouteRedirectsToUnifiedSalonPage() throws Exception {
        mockMvc.perform(get("/salons/branches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons"));
    }

    @Test
    void kakaoSearchMessageShownOnMergedPageWhenKeyMissing() throws Exception {
        mockMvc.perform(get("/salons").param("keyword", "cut"))
                .andExpect(status().isOk())
                .andExpect(view().name("salon/list"))
                .andExpect(model().attribute("pageTitle", "살롱 통합 탐색"))
                .andExpect(model().attribute("searchAction", "/salons"))
                .andExpect(model().attribute("branchMapStatus", "카카오 REST API 키가 설정되지 않아 지도를 표시할 수 없습니다."))
                .andExpect(model().attribute("kakaoResultsMessage", "카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다."));
    }

    @Test
    void legacyKakaoRouteRedirectsToUnifiedSalonPage() throws Exception {
        mockMvc.perform(get("/salons/kakao-search").param("keyword", "cut"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/salons?keyword=cut"));
    }
}

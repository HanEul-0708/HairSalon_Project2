package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    private BoardService boardService;

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
        stubBoardPagesEmpty();

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
    void mixedRegionAndServiceKeywordBuildsRegionAwareRequests() throws Exception {
        when(salonQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        when(designerQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        when(salonServiceQueryService.list(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        stubBoardPagesEmpty();

        mockMvc.perform(get("/search").param("keyword", "부산 수영구 펌"))
                .andExpect(status().isOk())
                .andExpect(view().name("search/results"))
                .andExpect(model().attribute("keyword", "부산 수영구 펌"))
                .andExpect(model().attribute("searchKeyword", "펌"))
                .andExpect(model().attribute("searchCity", "부산"))
                .andExpect(model().attribute("searchDistrict", "수영구"));

        ArgumentCaptor<SalonSearchRequest> salonRequest = ArgumentCaptor.forClass(SalonSearchRequest.class);
        ArgumentCaptor<DesignerSearchRequest> designerRequest = ArgumentCaptor.forClass(DesignerSearchRequest.class);
        ArgumentCaptor<SalonServiceSearchRequest> serviceRequest = ArgumentCaptor.forClass(SalonServiceSearchRequest.class);

        verify(salonQueryService).search(salonRequest.capture(), eq(0), eq(4));
        verify(designerQueryService).search(designerRequest.capture(), eq(0), eq(4));
        verify(salonServiceQueryService).list(serviceRequest.capture(), eq(0), eq(4));
        verify(boardService).getNoticePage(0, 4, "펌");

        assertThat(salonRequest.getValue().getKeyword()).isEqualTo("펌");
        assertThat(salonRequest.getValue().getCity()).isEqualTo("부산");
        assertThat(salonRequest.getValue().getDistrict()).isEqualTo("수영구");
        assertThat(salonRequest.getValue().getNeighborhood()).isNull();

        assertThat(designerRequest.getValue().getKeyword()).isEqualTo("펌");
        assertThat(designerRequest.getValue().getCity()).isEqualTo("부산");
        assertThat(designerRequest.getValue().getDistrict()).isEqualTo("수영구");
        assertThat(designerRequest.getValue().getNeighborhood()).isNull();

        assertThat(serviceRequest.getValue().getKeyword()).isEqualTo("펌");
        assertThat(serviceRequest.getValue().getCity()).isEqualTo("부산");
        assertThat(serviceRequest.getValue().getDistrict()).isEqualTo("수영구");
        assertThat(serviceRequest.getValue().getNeighborhood()).isNull();
    }

    @Test
    void blankKeywordRedirectsHome() throws Exception {
        mockMvc.perform(get("/search").param("keyword", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    void searchPageShowsNoticeAndAccessibleQnaResults() throws Exception {
        when(salonQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        when(designerQueryService.search(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        when(salonServiceQueryService.list(any(), eq(0), eq(4))).thenReturn(new PageImpl<>(List.of()));
        when(boardService.getNoticePage(eq(0), eq(4), eq("예약")))
                .thenReturn(new PageImpl<>(List.of(boardResponse(1, BoardType.NOTICE, "예약 안내", "admin01"))));
        when(boardService.getQnaPage(eq(0), eq(4), eq("예약"), eq("designer01"), eq(true)))
                .thenReturn(new PageImpl<>(List.of(boardResponse(2, BoardType.QNA, "예약 문의", "user01"))));

        mockMvc.perform(get("/search")
                        .with(authenticationFor("designer01", MemberRole.DESIGNER))
                        .param("keyword", "예약"))
                .andExpect(status().isOk())
                .andExpect(view().name("search/results"))
                .andExpect(model().attribute("noticeCount", 1L))
                .andExpect(model().attribute("qnaCount", 1L))
                .andExpect(model().attribute("hasAnyResult", true));
    }

    private void stubBoardPagesEmpty() {
        when(boardService.getNoticePage(eq(0), eq(4), any())).thenReturn(new PageImpl<>(List.of()));
        when(boardService.getQnaPage(eq(0), eq(4), any(), any(), anyBoolean())).thenReturn(new PageImpl<>(List.of()));
    }

    private BoardResponse boardResponse(Integer boardId, BoardType type, String title, String memberId) {
        return new BoardResponse(
                boardId,
                type,
                title,
                memberId,
                0,
                LocalDateTime.of(2026, 4, 13, 10, 0),
                false,
                0L,
                false,
                0,
                false
        );
    }

    private RequestPostProcessor authenticationFor(String memberId, MemberRole role) {
        Member member = Member.builder()
                .memberId(memberId)
                .password("encoded-password")
                .name("Tester")
                .phone("010-1234-5678")
                .email(memberId + "@example.com")
                .role(role)
                .status(MemberStatus.ACTIVE)
                .build();

        CustomUserDetails principal = new CustomUserDetails(member);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}

package com.hairsalonproject2.member.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.DesignerSignupSalonOptionResponse;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void anonymousUserCanOpenSignupPage() throws Exception {
        when(memberService.getDesignerSignupCityOptions()).thenReturn(List.of("부산광역시"));
        when(memberService.getDesignerSignupDistrictOptions(null)).thenReturn(List.of());
        when(memberService.getDesignerSignupNeighborhoodOptions(null, null)).thenReturn(List.of());
        when(memberService.getAvailableDesignerSalons(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/members/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeExists("memberSignupRequest"));
    }

    @Test
    void anonymousUserCanFetchDesignerSignupSalonOptions() throws Exception {
        when(memberService.getAvailableDesignerSalons("부산광역시", "수영구", "광안동"))
                .thenReturn(List.of(DesignerSignupSalonOptionResponse.builder()
                        .salonId(1)
                        .salonName("광안 살롱")
                        .address("부산광역시 수영구 광안동")
                        .build()));

        mockMvc.perform(get("/members/signup/salon-options")
                        .param("city", "부산광역시")
                        .param("district", "수영구")
                        .param("neighborhood", "광안동"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[{\"salonId\":1,\"salonName\":\"광안 살롱\",\"address\":\"부산광역시 수영구 광안동\"}]"));
    }

    @Test
    void anonymousUserCanCheckMemberIdAvailability() throws Exception {
        when(memberService.isMemberIdAvailable("user01")).thenReturn(true);

        mockMvc.perform(get("/members/check-id").param("memberId", "user01"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
    }

    @Test
    void anonymousUserCanCheckEmailAvailability() throws Exception {
        when(memberService.isEmailAvailable("tester@example.com")).thenReturn(true);

        mockMvc.perform(get("/members/check-email").param("email", "tester@example.com"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
    }

    @Test
    void anonymousUserCanCheckPhoneAvailability() throws Exception {
        when(memberService.isPhoneAvailable("010-1234-5678")).thenReturn(true);

        mockMvc.perform(get("/members/check-phone").param("phone", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
    }

    @Test
    void authenticatedUserCanCheckOwnEmailAvailabilityForUpdate() throws Exception {
        when(memberService.isEmailAvailableForUpdate("user01", "tester@example.com")).thenReturn(true);

        mockMvc.perform(get("/members/me/check-email")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .param("email", "tester@example.com"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
    }

    @Test
    void authenticatedUserCanCheckOwnPhoneAvailabilityForUpdate() throws Exception {
        when(memberService.isPhoneAvailableForUpdate("user01", "010-1234-5678")).thenReturn(true);

        mockMvc.perform(get("/members/me/check-phone")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .param("phone", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
    }

    @Test
    void authenticatedUserIsRedirectedFromLoginPageToMyPage() throws Exception {
        mockMvc.perform(get("/members/login").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/me"));
    }

    @Test
    void anonymousUserIsRedirectedToLoginForMyPage() throws Exception {
        mockMvc.perform(get("/members/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/members/login"));
    }

    @Test
    void signupPostWithValidRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/members/signup")
                        .with(csrf())
                        .param("role", "USER")
                        .param("memberId", "user01")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("name", "Tester")
                        .param("phone", "010-1234-5678")
                        .param("email", "tester@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/login?signup=true"));

        verify(memberService).signup(any());
    }

    @Test
    void signupPostWithInvalidRequestReturnsSignupView() throws Exception {
        when(memberService.getDesignerSignupCityOptions()).thenReturn(List.of("부산광역시"));
        when(memberService.getDesignerSignupDistrictOptions(null)).thenReturn(List.of());
        when(memberService.getDesignerSignupNeighborhoodOptions(null, null)).thenReturn(List.of());
        when(memberService.getAvailableDesignerSalons(null, null, null)).thenReturn(List.of());

        mockMvc.perform(post("/members/signup")
                        .with(csrf())
                        .param("role", "USER")
                        .param("memberId", "u")
                        .param("password", "123")
                        .param("passwordConfirm", "123")
                        .param("name", "")
                        .param("phone", "invalid")
                        .param("email", "bad-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("member/signup"));

        verify(memberService, never()).signup(any());
    }

    @Test
    void deleteMemberWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/members/me/delete").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isForbidden());

        verify(memberService, never()).softDeleteSelf(any());
    }

    @Test
    void deleteMemberWithCsrfDeletesCurrentUser() throws Exception {
        mockMvc.perform(post("/members/me/delete")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/logout"));

        verify(memberService).softDeleteSelf("user01");
    }

    @Test
    void authenticatedUserCanOpenMyPage() throws Exception {
        when(memberService.getMyDetail("user01")).thenReturn(MemberDetailResponse.builder()
                .memberId("user01")
                .name("Tester")
                .phone("010-1234-5678")
                .email("tester@example.com")
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 4, 6, 10, 0))
                .build());

        mockMvc.perform(get("/members/me").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isOk())
                .andExpect(view().name("member/mypage"))
                .andExpect(model().attributeExists("member"))
                .andExpect(model().attributeExists("memberUpdateRequest"))
                .andExpect(model().attributeExists("memberPasswordChangeRequest"));

        verify(memberService).getMyDetail(eq("user01"));
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

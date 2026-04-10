package com.hairsalonproject2.member.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberFlowMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void signupMyPageAndProfileEditFlow() throws Exception {
        when(memberService.getMyDetail("user01")).thenReturn(MemberDetailResponse.builder()
                .memberId("user01")
                .name("Tester")
                .phone("010-1234-5678")
                .email("tester@example.com")
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                .build());

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

        mockMvc.perform(get("/members/me").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isOk())
                .andExpect(view().name("member/mypage"));

        mockMvc.perform(post("/members/me/edit")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .with(csrf())
                        .param("name", "Edited User")
                        .param("phone", "010-9999-9999")
                        .param("email", "edited@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members/me"));

        InOrder inOrder = inOrder(memberService);
        inOrder.verify(memberService).signup(any());
        inOrder.verify(memberService).getMyDetail("user01");
        inOrder.verify(memberService).updateMyProfile(eq("user01"), any());
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

package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.member.service.DummyMemberSeeder;
import com.hairsalonproject2.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@Import(SecurityConfig.class)
class AdminMemberSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private DummyMemberSeeder dummyMemberSeeder;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void anonymousUserIsRedirectedToLoginForAdminPage() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/members/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotAccessAdminPage() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPostWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/admin/members/user01/role").param("role", "USER"))
                .andExpect(status().isForbidden());

        verify(memberService, never()).changeMemberRoleByAdmin(anyString(), anyString(), any());
    }

    @Test
    void adminPostWithCsrfIsAllowed() throws Exception {
        mockMvc.perform(post("/admin/members/user01/role")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf())
                        .param("role", "USER"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanFilterMemberListByStatus() throws Exception {
        when(memberService.searchMembers(eq(null), eq(null), eq(MemberStatus.INACTIVE), eq("latest"), eq(0)))
                .thenReturn(new PageImpl<>(List.of(MemberSummaryResponse.builder()
                        .memberId("user01")
                        .name("Tester")
                        .phone("010-1234-5678")
                        .email("tester@example.com")
                        .role(MemberRole.USER)
                        .status(MemberStatus.INACTIVE)
                        .createdAt(LocalDateTime.of(2026, 4, 6, 10, 0))
                        .build())));

        mockMvc.perform(get("/admin/members").param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("selectedStatus"));
    }

    private RequestPostProcessor authenticationFor(String memberId, MemberRole role) {
        Member member = Member.builder()
                .memberId(memberId)
                .password("encoded-password")
                .name("Admin")
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

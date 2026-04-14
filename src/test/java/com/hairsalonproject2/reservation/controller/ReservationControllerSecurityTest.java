package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import com.hairsalonproject2.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void normalUserCannotGetAllReservations() throws Exception {
        mockMvc.perform(get("/api/reservations").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetAllReservations() throws Exception {
        when(reservationService.getAllReservations()).thenReturn(List.of(sampleReservation()));

        mockMvc.perform(get("/api/reservations").with(authenticationFor("admin01", MemberRole.ADMIN)))
                .andExpect(status().isOk());

        verify(reservationService).getAllReservations();
    }

    @Test
    void normalUserCannotPatchReservationStatus() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/status")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());

        verify(reservationService, never()).updateReservationStatus(eq(1), any(ReservationStatusUpdateRequest.class));
    }

    @Test
    void adminCanPatchReservationStatus() throws Exception {
        when(reservationService.updateReservationStatus(eq(1), any(ReservationStatusUpdateRequest.class)))
                .thenReturn(sampleReservation());

        mockMvc.perform(patch("/api/reservations/1/status")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        verify(reservationService).updateReservationStatus(eq(1), any(ReservationStatusUpdateRequest.class));
    }

    @Test
    void designerCanPatchReservationStatusAfterSalonAccessCheck() throws Exception {
        when(reservationService.updateReservationStatus(eq(1), any(ReservationStatusUpdateRequest.class)))
                .thenReturn(sampleReservation());

        mockMvc.perform(patch("/api/reservations/1/status")
                        .with(authenticationFor("designer01", MemberRole.DESIGNER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        verify(reservationService).validateReservationDesignerSalonAccess(1, "designer01");
        verify(reservationService).updateReservationStatus(eq(1), any(ReservationStatusUpdateRequest.class));
    }

    private ReservationResponse sampleReservation() {
        return ReservationResponse.builder()
                .reservationId(1)
                .memberId("user01")
                .designerId(1)
                .designerName("Designer")
                .salonServiceId(1)
                .serviceName("Cut")
                .reservationDate(LocalDate.of(2026, 4, 8))
                .reservationTime(LocalTime.of(10, 0))
                .createdAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                .build();
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

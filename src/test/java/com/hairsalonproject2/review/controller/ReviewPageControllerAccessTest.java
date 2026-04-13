package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReviewPageController.class)
@Import(SecurityConfig.class)
class ReviewPageControllerAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private ReviewRepository reviewRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void reviewListHandlesNullFilterOptionNames() throws Exception {
        when(reviewService.getAllReviews(null, null, "latest"))
                .thenReturn(List.of(sampleReview(1, null, 1, null, "서울특별시 강남구 역삼동")));

        mockMvc.perform(get("/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("review/list"))
                .andExpect(model().attributeExists("salonOptions", "designerOptions"));
    }

    @Test
    void ownerCanOpenReviewWritePage() throws Exception {
        when(reservationService.getReservation(1)).thenReturn(sampleReservation("user01"));
        when(reviewRepository.findByReservation_ReservationId(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/reviews/write")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .param("reservationId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("review/write"));
    }

    @Test
    void ownerCannotOpenReviewWritePageForReservedReservation() throws Exception {
        when(reservationService.getReservation(1)).thenReturn(sampleReservation("user01", ReservationStatus.RESERVED));

        mockMvc.perform(get("/reviews/write")
                        .with(authenticationFor("user01", MemberRole.USER))
                        .param("reservationId", "1"))
                .andExpect(status().isBadRequest());

        verify(reviewRepository, never()).findByReservation_ReservationId(anyInt());
    }

    @Test
    void adminCannotOpenOtherMembersReviewWritePage() throws Exception {
        when(reservationService.getReservation(1)).thenReturn(sampleReservation("user01"));

        mockMvc.perform(get("/reviews/write")
                        .with(authenticationFor("admin01", MemberRole.ADMIN))
                        .param("reservationId", "1"))
                .andExpect(status().isForbidden());

        verify(reviewRepository, never()).findByReservation_ReservationId(anyInt());
    }

    @Test
    void otherUserCannotOpenReviewEditPage() throws Exception {
        when(reviewService.getReviewDetail(1, "user02")).thenReturn(new ReviewDetailResponse(
                1,
                1,
                LocalDate.of(2026, 4, 8),
                LocalTime.of(10, 0),
                "user01",
                "Writer",
                1,
                "Designer",
                "Cut",
                (byte) 5,
                "Great",
                null,
                null,
                LocalDateTime.of(2026, 4, 8, 11, 0),
                0,
                false,
                List.of()
        ));

        mockMvc.perform(get("/reviews/1/edit")
                        .with(authenticationFor("user02", MemberRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/access-denied"));
    }

    private ReservationResponse sampleReservation(String memberId) {
        return sampleReservation(memberId, ReservationStatus.COMPLETED);
    }

    private ReservationResponse sampleReservation(String memberId, ReservationStatus status) {
        return ReservationResponse.builder()
                .reservationId(1)
                .memberId(memberId)
                .designerId(1)
                .designerName("Designer")
                .salonServiceId(1)
                .serviceName("Cut")
                .reservationDate(LocalDate.of(2026, 4, 8))
                .reservationTime(LocalTime.of(10, 0))
                .status(status)
                .createdAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                .build();
    }

    private ReviewResponse sampleReview(Integer salonId,
                                        String salonName,
                                        Integer designerId,
                                        String designerName,
                                        String salonAddress) {
        return new ReviewResponse(
                1,
                1,
                LocalDate.of(2026, 4, 8),
                LocalTime.of(10, 0),
                "user01",
                "Writer",
                designerId,
                designerName,
                salonId,
                salonName,
                salonAddress,
                "Cut",
                (byte) 5,
                "Great",
                null,
                null,
                LocalDateTime.of(2026, 4, 8, 11, 0),
                null,
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

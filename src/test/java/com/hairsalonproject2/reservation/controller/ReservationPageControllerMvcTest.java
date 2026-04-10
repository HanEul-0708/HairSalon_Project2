package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationServiceImpl;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReservationPageController.class)
@Import(SecurityConfig.class)
class ReservationPageControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DesignerQueryService designerQueryService;

    @MockitoBean
    private SalonServiceQueryService salonServiceQueryService;

    @MockitoBean
    private SalonQueryService salonQueryService;

    @MockitoBean
    private ReservationServiceImpl reservationServiceImpl;

    @MockitoBean
    private ReviewRepository reviewRepository;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void userReservationPageShowsOwnReservationsAndReviews() throws Exception {
        ReservationResponse reservation = sampleReservation("user01", "Designer A");
        ReviewResponse review = sampleReview("user01", "Designer A");

        when(reservationServiceImpl.getMyReservations("user01")).thenReturn(List.of(reservation));
        when(reviewRepository.findByReservation_ReservationIdIn(List.of(1))).thenReturn(List.of());
        when(reviewService.getReviewsByMember("user01", "user01")).thenReturn(List.of(review));

        mockMvc.perform(get("/members/me/reservations").with(authenticationFor("user01", MemberRole.USER)))
                .andExpect(status().isOk())
                .andExpect(view().name("reservation/list"))
                .andExpect(model().attribute("isDesignerReservationView", false))
                .andExpect(model().attribute("reservationPagePath", "/members/me/reservations"))
                .andExpect(model().attributeExists("reservations", "relatedReviews"));

        verify(reservationServiceImpl).getMyReservations("user01");
        verify(reviewService).getReviewsByMember("user01", "user01");
    }

    @Test
    void designerReservationPageShowsAssignedReservationsAndReceivedReviews() throws Exception {
        ReservationResponse reservation = sampleReservation("user01", "Designer Kim");
        ReviewResponse review = sampleReview("user01", "Designer Kim");

        when(reservationServiceImpl.getReservationsByDesignerMember("designer01")).thenReturn(List.of(reservation));
        when(reviewRepository.findByReservation_ReservationIdIn(List.of(1))).thenReturn(List.of());
        when(reviewService.getReviewsByDesignerMember("designer01", "designer01")).thenReturn(List.of(review));

        mockMvc.perform(get("/designers/me/reservations").with(authenticationFor("designer01", MemberRole.DESIGNER)))
                .andExpect(status().isOk())
                .andExpect(view().name("reservation/list"))
                .andExpect(model().attribute("isDesignerReservationView", true))
                .andExpect(model().attribute("reservationPagePath", "/designers/me/reservations"))
                .andExpect(model().attributeExists("reservations", "relatedReviews"));

        verify(reservationServiceImpl).getReservationsByDesignerMember("designer01");
        verify(reviewService).getReviewsByDesignerMember("designer01", "designer01");
    }

    private ReservationResponse sampleReservation(String memberId, String designerName) {
        return ReservationResponse.builder()
                .reservationId(1)
                .memberId(memberId)
                .designerId(10)
                .designerName(designerName)
                .salonServiceId(100)
                .serviceName("컷")
                .reservationDate(LocalDate.of(2026, 4, 8))
                .reservationTime(LocalTime.of(10, 0))
                .status(ReservationStatus.COMPLETED)
                .totalPrice(30000)
                .paymentMethodLabel("Card")
                .createdAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                .build();
    }

    private ReviewResponse sampleReview(String memberId, String designerName) {
        return new ReviewResponse(
                1,
                1,
                LocalDate.of(2026, 4, 8),
                LocalTime.of(10, 0),
                memberId,
                "테스터",
                10,
                designerName,
                1,
                "살롱",
                "서울 강남구",
                "컷",
                (byte) 5,
                "좋았습니다.",
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

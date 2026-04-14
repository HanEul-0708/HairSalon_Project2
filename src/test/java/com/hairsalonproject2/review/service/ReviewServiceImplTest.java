package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewImageRepository;
import com.hairsalonproject2.review.repository.ReviewLikeRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.service.SalonRatingSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @Mock
    private ReviewLikeRepository reviewLikeRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private SalonRatingSyncService salonRatingSyncService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReviewRejectsReservationThatIsNotCompleted() {
        Reservation reservation = reservationWithStatus(ReservationStatus.RESERVED);
        ReviewCreateRequest request = new ReviewCreateRequest();
        ReflectionTestUtils.setField(request, "reservationId", 1);
        ReflectionTestUtils.setField(request, "rating", (byte) 5);
        ReflectionTestUtils.setField(request, "content", "후기");

        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reviewService.createReview("user01", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only completed reservations can be reviewed.");

        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getReviewsByDesignerMemberReturnsReviewsForEntireSalon() {
        Salon salon = Salon.builder().salonId(3).name("Salon Three").address("Seoul").build();
        Designer linkedDesigner = Designer.builder().designerId(1).name("Manager").salon(salon).build();
        Review firstReview = reviewForSalon(salon, 11, "Senior", 101, LocalDateTime.of(2026, 4, 10, 12, 0));
        Review secondReview = reviewForSalon(salon, 12, "Junior", 102, LocalDateTime.of(2026, 4, 9, 12, 0));

        when(designerRepository.findByMember_MemberId("designer01")).thenReturn(Optional.of(linkedDesigner));
        when(reviewRepository.findByDesigner_Salon_SalonId(3)).thenReturn(List.of(secondReview, firstReview));

        assertThat(reviewService.getReviewsByDesignerMember("designer01", "designer01"))
                .hasSize(2)
                .extracting("designerName")
                .containsExactly("Senior", "Junior");
    }

    @Test
    void getAllReviewsSortsNullCreatedAtLast() {
        Salon salon = Salon.builder().salonId(3).name("Salon Three").address("Seoul").build();
        Review nullCreatedAtReview = reviewForSalon(salon, 11, "Senior", 101, null);
        Review latestReview = reviewForSalon(salon, 12, "Junior", 102, LocalDateTime.of(2026, 4, 10, 12, 0));

        when(reviewRepository.findAll()).thenReturn(List.of(nullCreatedAtReview, latestReview));

        assertThat(reviewService.getAllReviews(null, null, "latest"))
                .extracting("reviewId")
                .containsExactly(102, 101);
    }

    private Reservation reservationWithStatus(ReservationStatus status) {
        Member member = Member.builder()
                .memberId("user01")
                .name("Tester")
                .build();

        Reservation reservation = Reservation.builder()
                .member(member)
                .reservationDate(LocalDate.of(2026, 4, 10))
                .reservationTime(LocalTime.of(10, 0))
                .status(status)
                .build();
        ReflectionTestUtils.setField(reservation, "reservationId", 1);
        return reservation;
    }

    private Review reviewForSalon(Salon salon,
                                  Integer designerId,
                                  String designerName,
                                  Integer reservationId,
                                  LocalDateTime createdAt) {
        Member member = Member.builder()
                .memberId("user01")
                .name("Tester")
                .build();
        Designer designer = Designer.builder()
                .designerId(designerId)
                .name(designerName)
                .salon(salon)
                .build();
        Reservation reservation = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(com.hairsalonproject2.salonservice.entity.SalonService.builder()
                        .serviceId(1)
                        .name("Cut")
                        .salon(salon)
                        .build())
                .reservationDate(LocalDate.of(2026, 4, 10))
                .reservationTime(LocalTime.of(10, 0))
                .status(ReservationStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

        Review review = Review.builder()
                .reservation(reservation)
                .member(member)
                .designer(designer)
                .rating((byte) 5)
                .content("good")
                .build();
        ReflectionTestUtils.setField(review, "createdAt", createdAt);
        ReflectionTestUtils.setField(review, "reviewId", reservationId);
        return review;
    }
}

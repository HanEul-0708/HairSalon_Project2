package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewDummySeederTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private DummyTimelineService dummyTimelineService;

    @InjectMocks
    private ReviewDummySeeder reviewDummySeeder;

    @Test
    void seedReviewsFromCompletedReservationsCreatesOnlyMissingReviews() {
        Salon salon = Salon.builder().salonId(1).name("테스트살롱").build();
        Designer designer = Designer.builder().designerId(1).salon(salon).name("김하늘 점장").build();
        Member member = Member.builder().memberId("user1").name("회원1").build();
        SalonService service = SalonService.builder().serviceId(1).salon(salon).name("여성 커트").build();

        Reservation first = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(service)
                .reservationDate(LocalDate.now().minusDays(2))
                .reservationTime(LocalTime.of(10, 0))
                .status(ReservationStatus.COMPLETED)
                .totalPrice(30000)
                .build();
        Reservation second = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(service)
                .reservationDate(LocalDate.now().minusDays(1))
                .reservationTime(LocalTime.of(11, 0))
                .status(ReservationStatus.COMPLETED)
                .totalPrice(30000)
                .build();

        setReservationId(first, 1);
        setReservationId(second, 2);

        when(reservationRepository.findByStatus(ReservationStatus.COMPLETED)).thenReturn(List.of(first, second));
        when(reviewRepository.findByReservation_ReservationId(1)).thenReturn(Optional.empty());
        when(reviewRepository.findByReservation_ReservationId(2)).thenReturn(Optional.of(Review.builder()
                .reservation(second)
                .member(member)
                .designer(designer)
                .rating((byte) 5)
                .content("기존 리뷰")
                .build()));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int created = reviewDummySeeder.seedReviewsFromCompletedReservations();

        assertThat(created).isEqualTo(1);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository, times(1)).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getContent()).isNotBlank();
        assertThat(reviewCaptor.getValue().getRating()).isBetween((byte) 1, (byte) 5);
        verify(reviewRepository, times(1)).findByReservation_ReservationId(1);
        verify(reviewRepository, times(1)).findByReservation_ReservationId(2);
    }

    private void setReservationId(Reservation reservation, int reservationId) {
        org.springframework.test.util.ReflectionTestUtils.setField(reservation, "reservationId", reservationId);
    }
}

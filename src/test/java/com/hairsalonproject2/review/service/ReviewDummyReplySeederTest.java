package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.service.SalonRatingSyncService;
import com.hairsalonproject2.salonservice.entity.SalonService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewDummyReplySeederTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private DummyTimelineService dummyTimelineService;

    @Mock
    private SalonRatingSyncService salonRatingSyncService;

    @InjectMocks
    private ReviewDummySeeder reviewDummySeeder;

    @Test
    void seedReviewRepliesCreatesRepliesOnlyForReviewsWithoutReply() {
        Review unansweredReview = createReview(1, "답변 없는 리뷰", null, null);
        Review answeredReview = createReview(2, "이미 답변된 리뷰", "기존 답변", LocalDateTime.now().minusDays(1));

        when(reviewRepository.findAll()).thenReturn(List.of(answeredReview, unansweredReview));

        int created = reviewDummySeeder.seedReviewReplies();

        assertThat(created).isEqualTo(1);
        assertThat(unansweredReview.getReplyContent()).isNotBlank();
        assertThat(unansweredReview.getReplyCreatedAt()).isAfter(unansweredReview.getCreatedAt());
        assertThat(answeredReview.getReplyContent()).isEqualTo("기존 답변");
    }

    private Review createReview(int reviewId, String content, String replyContent, LocalDateTime replyCreatedAt) {
        Salon salon = Salon.builder().salonId(1).name("테스트살롱").build();
        Designer designer = Designer.builder().designerId(1).name("김디자이너").salon(salon).build();
        Member member = Member.builder().memberId("user" + reviewId).name("고객" + reviewId).build();
        SalonService salonService = SalonService.builder().serviceId(1).name("커트").salon(salon).build();
        Reservation reservation = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(salonService)
                .reservationDate(LocalDate.now().minusDays(2))
                .reservationTime(LocalTime.of(14, 0))
                .totalPrice(30000)
                .build();

        Review review = Review.builder()
                .reservation(reservation)
                .member(member)
                .designer(designer)
                .rating((byte) 5)
                .content(content)
                .replyContent(replyContent)
                .replyCreatedAt(replyCreatedAt)
                .build();

        ReflectionTestUtils.setField(review, "reviewId", reviewId);
        ReflectionTestUtils.setField(review, "createdAt", LocalDateTime.now().minusDays(2));
        return review;
    }
}

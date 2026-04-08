package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewDummySeeder {

    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final DummyTimelineService dummyTimelineService;

    @Transactional
    public int seedReviewsFromCompletedReservations() {
        List<Reservation> completedReservations = reservationRepository.findByStatus(ReservationStatus.COMPLETED)
                .stream()
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();

        int created = 0;
        for (int index = 0; index < completedReservations.size(); index++) {
            Reservation reservation = completedReservations.get(index);
            if (reviewRepository.findByReservation_ReservationId(reservation.getReservationId()).isPresent()) {
                continue;
            }

            byte rating = determineRating(index, reservation);
            Review review = Review.builder()
                    .reservation(reservation)
                    .member(reservation.getMember())
                    .designer(reservation.getDesigner())
                    .rating(rating)
                    .content(buildContent(reservation, rating, index))
                    .build();
            Review savedReview = reviewRepository.save(review);
            dummyTimelineService.alignReviewTimeline(
                    savedReview.getReviewId(),
                    reservation.getReservationId(),
                    reservation.getReservationDate(),
                    reservation.getReservationTime()
            );
            created++;
        }
        return created;
    }

    private byte determineRating(int index, Reservation reservation) {
        String serviceName = reservation.getSalonService().getName() == null ? "" : reservation.getSalonService().getName();
        if (serviceName.contains("커트")) {
            return (byte) ((index % 4 == 0) ? 5 : (index % 4 == 1) ? 4 : (index % 4 == 2) ? 5 : 3);
        }
        if (serviceName.contains("염색")) {
            return (byte) ((index % 4 == 0) ? 5 : (index % 4 == 1) ? 4 : (index % 4 == 2) ? 4 : 3);
        }
        if (serviceName.contains("펌")) {
            return (byte) ((index % 5 == 0) ? 5 : (index % 5 == 1) ? 4 : (index % 5 == 2) ? 3 : (index % 5 == 3) ? 4 : 2);
        }
        return (byte) ((index % 5 == 0) ? 5 : (index % 5 == 1) ? 4 : (index % 5 == 2) ? 4 : (index % 5 == 3) ? 3 : 2);
    }

    private String buildContent(Reservation reservation, byte rating, int index) {
        String serviceName = reservation.getSalonService().getName();
        String designerName = reservation.getDesigner().getName();

        if (rating >= 5) {
            return switch (index % 4) {
                case 0 -> designerName + " 디자이너가 상담부터 마무리까지 세심해서 " + serviceName + " 결과가 정말 만족스러웠어요.";
                case 1 -> serviceName + " 스타일이 기대 이상으로 잘 나와서 다음에도 다시 예약하고 싶습니다.";
                case 2 -> "손질 방법까지 자세히 알려줘서 " + serviceName + " 후 관리가 훨씬 편해졌어요.";
                default -> "매장 분위기와 응대가 좋았고 " + designerName + " 디자이너 실력도 확실히 느껴졌습니다.";
            };
        }

        if (rating == 4) {
            return switch (index % 4) {
                case 0 -> serviceName + " 결과는 전반적으로 만족스럽고 상담도 편안하게 진행됐습니다.";
                case 1 -> designerName + " 디자이너가 원하는 느낌을 잘 맞춰줘서 무난하게 만족했어요.";
                case 2 -> "시술 시간이 조금 길었지만 " + serviceName + " 완성도는 괜찮았습니다.";
                default -> "전체적으로 깔끔했고 다음에도 상황에 따라 다시 방문할 것 같아요.";
            };
        }

        if (rating == 3) {
            return switch (index % 4) {
                case 0 -> serviceName + " 결과가 나쁘진 않았지만 기대했던 느낌과는 조금 차이가 있었습니다.";
                case 1 -> "상담과 시술은 무난했지만 조금 더 세밀한 안내가 있었으면 좋겠어요.";
                case 2 -> designerName + " 디자이너의 응대는 좋았지만 스타일 완성도는 보통이었습니다.";
                default -> "전체적으로 무난한 편이었고 다음 예약은 조금 더 고민해볼 것 같습니다.";
            };
        }

        return switch (index % 4) {
            case 0 -> serviceName + " 시술 결과가 기대보다 아쉬워서 다음에는 다른 스타일을 상담해보고 싶어요.";
            case 1 -> "대기 시간이 길고 마무리가 조금 급하게 느껴졌습니다.";
            case 2 -> designerName + " 디자이너와 소통이 완전히 맞지는 않아 아쉬움이 남았습니다.";
            default -> "전체적으로 아쉬운 점이 있었지만 다음에는 더 잘 맞는 스타일을 찾고 싶습니다.";
        };
    }
}

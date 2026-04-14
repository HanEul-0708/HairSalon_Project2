package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.service.SalonRatingSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewDummySeeder {

    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final DummyTimelineService dummyTimelineService;
    private final SalonRatingSyncService salonRatingSyncService;

    @Transactional
    public int seedReviewsFromCompletedReservations() {
        List<Reservation> completedReservations = reservationRepository.findByStatus(ReservationStatus.COMPLETED).stream()
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
        LinkedHashSet<Integer> salonIdsToSync = completedReservations.stream()
                .map(reservation -> reservation.getDesigner().getSalon().getSalonId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

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

        salonRatingSyncService.syncSalonStats(List.copyOf(salonIdsToSync));
        return created;
    }

    @Transactional
    public int seedReviewReplies() {
        List<Review> reviewsWithoutReply = reviewRepository.findAll().stream()
                .filter(review -> review.getReplyContent() == null || review.getReplyContent().isBlank())
                .sorted(Comparator.comparing(Review::getReviewId))
                .toList();

        int created = 0;
        for (int index = 0; index < reviewsWithoutReply.size(); index++) {
            Review review = reviewsWithoutReply.get(index);
            LocalDateTime replyCreatedAt = review.getCreatedAt() == null
                    ? LocalDateTime.now().minusHours(index + 1L)
                    : review.getCreatedAt().plusHours(4L + (index % 3));
            review.updateReply(buildReplyContent(review, index), replyCreatedAt);
            created++;
        }

        return created;
    }

    private byte determineRating(int index, Reservation reservation) {
        String serviceName = reservation.getSalonService().getName() == null ? "" : reservation.getSalonService().getName();

        if (serviceName.contains("커트")) {
            return (byte) switch (index % 4) {
                case 0, 2 -> 5;
                case 1 -> 4;
                default -> 3;
            };
        }
        if (serviceName.contains("염색")) {
            return (byte) switch (index % 4) {
                case 0 -> 5;
                case 1, 2 -> 4;
                default -> 3;
            };
        }
        if (serviceName.contains("펌")) {
            return (byte) switch (index % 5) {
                case 0 -> 5;
                case 1, 3 -> 4;
                case 2 -> 3;
                default -> 2;
            };
        }
        return (byte) switch (index % 5) {
            case 0 -> 5;
            case 1, 2 -> 4;
            case 3 -> 3;
            default -> 2;
        };
    }

    private String buildContent(Reservation reservation, byte rating, int index) {
        String serviceName = reservation.getSalonService().getName();
        String designerName = reservation.getDesigner().getName();

        if (rating >= 5) {
            return switch (index % 4) {
                case 0 -> designerName + " 디자이너가 상담부터 마무리까지 친절해서 " + serviceName + " 결과가 정말 만족스러웠어요.";
                case 1 -> serviceName + " 스타일이 기대 이상으로 잘 나와서 다음에도 다시 예약하고 싶습니다.";
                case 2 -> "손질 방법까지 자세히 알려주셔서 " + serviceName + " 후 관리가 훨씬 편해졌어요.";
                default -> "매장 분위기도 좋았고 " + designerName + " 디자이너 실력이 확실해서 만족했습니다.";
            };
        }

        if (rating == 4) {
            return switch (index % 4) {
                case 0 -> serviceName + " 결과는 전반적으로 만족스럽고 상담도 편안하게 진행됐습니다.";
                case 1 -> designerName + " 디자이너가 요청한 부분을 잘 맞춰줘서 무난하게 만족했어요.";
                case 2 -> "시술 시간은 조금 길었지만 " + serviceName + " 완성도는 괜찮았습니다.";
                default -> "전체적으로 깔끔했고 다음에도 상황에 따라 다시 방문할 것 같아요.";
            };
        }

        if (rating == 3) {
            return switch (index % 4) {
                case 0 -> serviceName + " 결과가 나쁘진 않았지만 기대했던 느낌과는 조금 차이가 있었습니다.";
                case 1 -> "상담과 시술은 무난했지만 조금 더 자세한 안내가 있었으면 좋겠어요.";
                case 2 -> designerName + " 디자이너의 응대는 좋았지만 스타일 완성도는 보통이었습니다.";
                default -> "전체적으로 무난한 편이었고 다음 예약은 조금 더 고민해 볼 것 같습니다.";
            };
        }

        return switch (index % 4) {
            case 0 -> serviceName + " 시술 결과가 기대보다 아쉬워서 다음에는 다른 스타일을 상담받아 보고 싶어요.";
            case 1 -> "대기 시간이 길고 마무리가 조금 급하게 느껴졌습니다.";
            case 2 -> designerName + " 디자이너는 친절했지만 스타일 만족도는 아쉬웠습니다.";
            default -> "전체적으로 아쉬운 점이 있었지만 다음에는 더 잘 맞는 스타일을 찾고 싶습니다.";
        };
    }

    private String buildReplyContent(Review review, int index) {
        String designerName = review.getDesigner().getName();
        String serviceName = review.getReservation().getSalonService().getName();
        byte rating = review.getRating() == null ? 0 : review.getRating();

        if (rating >= 5) {
            return switch (index % 3) {
                case 0 -> designerName + " 디자이너입니다. 만족스러운 후기 남겨주셔서 감사합니다. 다음 방문 때도 지금 스타일 그대로 예쁘게 도와드리겠습니다.";
                case 1 -> "소중한 후기 감사합니다. " + serviceName + " 결과를 마음에 들어 해주셔서 정말 기쁩니다.";
                default -> "좋은 리뷰 남겨주셔서 감사합니다. 다음에도 편안하고 만족스러운 시술로 보답하겠습니다.";
            };
        }

        if (rating >= 3) {
            return switch (index % 3) {
                case 0 -> designerName + " 디자이너입니다. 남겨주신 의견 감사합니다. 다음 방문에서는 더 만족하실 수 있도록 세심하게 반영하겠습니다.";
                case 1 -> "후기 감사합니다. 아쉬웠던 부분은 내부적으로 다시 점검해서 다음 시술 때 개선해 보겠습니다.";
                default -> "소중한 피드백 감사합니다. 말씀해 주신 부분 참고해서 더 꼼꼼하게 안내드리겠습니다.";
            };
        }

        return switch (index % 3) {
            case 0 -> designerName + " 디자이너입니다. 만족을 드리지 못해 죄송합니다. 남겨주신 내용을 확인해 서비스 개선에 반영하겠습니다.";
            case 1 -> "불편을 드려 죄송합니다. 다음 방문 시에는 충분한 상담과 세심한 시술로 보완하겠습니다.";
            default -> "소중한 의견 감사합니다. 아쉬운 점은 매장에서 다시 확인하고 개선하겠습니다.";
        };
    }
}

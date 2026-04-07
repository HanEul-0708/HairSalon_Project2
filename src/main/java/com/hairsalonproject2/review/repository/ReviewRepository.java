package com.hairsalonproject2.review.repository;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

    Optional<Review> findByReservation_ReservationId(Integer reservationId);

    List<Review> findByMember_MemberId(String memberId);

    List<Review> findByDesigner_DesignerId(Integer designerId);

    List<Review> findByDesigner_Salon_SalonId(Integer salonId);

    // 예약 목록에서 "리뷰 작성" 버튼을 보여줄지 판단할 때 사용한다.
    List<Review> findByReservation_ReservationIdIn(List<Integer> reservationIds);

    @Query("select avg(r.rating) from Review r where r.designer.designerId = :designerId")
    Double findAverageRatingByDesignerId(Integer designerId);

    @Query("""
            select new com.hairsalonproject2.review.dto.DesignerRankingResponse(
                r.designer.designerId,
                r.designer.name,
                avg(r.rating),
                count(r)
            )
            from Review r
            group by r.designer.designerId, r.designer.name
            having count(r) >= :minReviewCount
            order by avg(r.rating) desc, count(r) desc
            """)
    List<DesignerRankingResponse> findTopDesignersByAverageRatingAndReviewCount(Long minReviewCount);

    @Query("""
            select new com.hairsalonproject2.review.dto.MonthlyReviewStatResponse(
                year(r.createdAt),
                month(r.createdAt),
                count(r),
                avg(r.rating)
            )
            from Review r
            group by year(r.createdAt), month(r.createdAt)
            order by year(r.createdAt) asc, month(r.createdAt) asc
            """)
    List<MonthlyReviewStatResponse> findMonthlyReviewStats();

    @Query("""
            select new com.hairsalonproject2.review.dto.MonthlyReviewStatResponse(
                year(r.createdAt),
                month(r.createdAt),
                count(r),
                round(avg(r.rating), 1)
            )
            from Review r
            where r.createdAt between :startDate and :endDate
            group by year(r.createdAt), month(r.createdAt)
            order by year(r.createdAt) asc, month(r.createdAt) asc
            """)
    List<MonthlyReviewStatResponse> findMonthlyReviewStatsByPeriod(LocalDateTime startDate,
                                                                   LocalDateTime endDate);

    @Query("""
            select new com.hairsalonproject2.review.dto.SalonRankingResponse(
                r.designer.salon.salonId,
                r.designer.salon.name,
                avg(r.rating),
                count(r)
            )
            from Review r
            group by r.designer.salon.salonId, r.designer.salon.name
            having count(r) >= :minReviewCount
            order by avg(r.rating) desc, count(r) desc
            """)
    List<SalonRankingResponse> findTopSalonsByAverageRating(Long minReviewCount);
}

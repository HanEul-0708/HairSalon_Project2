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

/**
 * ReviewRepository
 *
 * 리뷰 DB 접근 Repository
 */
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    /**
     * 예약 1건당 리뷰 1개 여부 확인
     */
    Optional<Review> findByReservation_ReservationId(Integer reservationId);

    /**
     * 회원별 리뷰 목록 조회
     */
    List<Review> findByMember_MemberId(String memberId);

    /**
     * 특정 디자이너의 평균 평점 조회
     */
    @Query("select avg(r.rating) from Review r where r.designer.designerId = :designerId")
    Double findAverageRatingByDesignerId(Integer designerId);

    /**
     * 리뷰 수가 일정 개수 이상인 디자이너 중
     * 평균 평점이 높은 순으로 랭킹 조회
     *
     * 정렬 기준
     * 1. 평균 평점 내림차순
     * 2. 리뷰 수 내림차순
     *
     * 평균 평점은 소수점 1자리 반올림
     */
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

    /**
     * 전체 월별 리뷰 수 / 평균 평점 집계 조회
     */
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
    /**
     * 특정 기간의 월별 리뷰 수 / 평균 평점 집계 조회
     *
     * createdAt 기준으로 연도/월 그룹화
     * 평균 평점은 소수점 1자리 반올림
     */
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
    /**
     * 미용실별 평균 평점 / 리뷰 수 랭킹 조회
     *
     * 디자이너가 소속된 미용실 기준으로 집계
     * 평균 평점은 소수점 1자리 반올림
     */
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
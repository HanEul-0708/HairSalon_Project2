package com.hairsalonproject2.review.repository;

import com.hairsalonproject2.review.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ReviewImageRepository
 *
 * 리뷰 이미지 DB 접근 Repository
 */
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Integer> {

    /**
     * 특정 리뷰에 연결된 이미지 목록 조회
     */
    List<ReviewImage> findByReview_ReviewId(Integer reviewId);
}
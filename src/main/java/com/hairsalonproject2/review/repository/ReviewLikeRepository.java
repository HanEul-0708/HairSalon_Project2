package com.hairsalonproject2.review.repository;

import com.hairsalonproject2.review.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Integer> {

 Optional<ReviewLike> findByReview_ReviewIdAndMember_MemberId(Integer reviewId, String memberId);

 boolean existsByReview_ReviewIdAndMember_MemberId(Integer reviewId, String memberId);

 Optional<ReviewLike> findByReview_ReviewIdAndVisitorToken(Integer reviewId, String visitorToken);

 boolean existsByReview_ReviewIdAndVisitorToken(Integer reviewId, String visitorToken);
}

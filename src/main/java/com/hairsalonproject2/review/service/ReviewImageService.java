package com.hairsalonproject2.review.service;

import com.hairsalonproject2.review.dto.ReviewImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ReviewImageService
 *
 * 리뷰 이미지 관련 서비스
 */
public interface ReviewImageService {

    /**
     * 리뷰 이미지 업로드
     */
    ReviewImageResponse uploadReviewImage(String loginMemberId, boolean isAdmin, Integer reviewId, MultipartFile file, Integer sortOrder);

    /**
     * 특정 리뷰의 이미지 목록 조회
     */
    List<ReviewImageResponse> getImagesByReview(Integer reviewId);

    /**
     * 리뷰 이미지 삭제
     */
    void deleteImage(String loginMemberId, boolean isAdmin, Integer imageId);
}

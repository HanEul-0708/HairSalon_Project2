package com.hairsalonproject2.review.service;

import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.entity.ReviewImage;
import com.hairsalonproject2.review.repository.ReviewImageRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ReviewImageServiceImpl
 *
 * 리뷰 이미지 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewImageServiceImpl implements ReviewImageService {

    /**
     * 리뷰 조회 Repository
     */
    private final ReviewRepository reviewRepository;

    /**
     * 리뷰 이미지 Repository
     */
    private final ReviewImageRepository reviewImageRepository;

    /**
     * 파일 저장 서비스
     */
    private final FileStorageService fileStorageService;

    /**
     * 리뷰 이미지 업로드
     */
    @Override
    @Transactional
    public ReviewImageResponse uploadReviewImage(Integer reviewId, MultipartFile file, Integer sortOrder) {

        // 1️⃣ 리뷰 존재 여부 확인
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않습니다."));

        // 2️⃣ 파일 저장 (서버 디스크 or S3 등)
        String imageUrl = fileStorageService.storeFile(file);

        // 3️⃣ sortOrder 기본값 처리
        if (sortOrder == null) {
            sortOrder = 0;
        }

        // 4️⃣ ReviewImage 엔티티 생성
        ReviewImage reviewImage = ReviewImage.builder()
                .review(review)
                .imageUrl(imageUrl)
                .sortOrder(sortOrder)
                .build();

        // 5️⃣ 양방향 연관관계 유지
        review.addReviewImage(reviewImage);

        // 6️⃣ DB 저장
        ReviewImage savedImage = reviewImageRepository.save(reviewImage);

        // 7️⃣ DTO 반환
        return new ReviewImageResponse(
                savedImage.getImageId(),
                savedImage.getImageUrl(),
                savedImage.getSortOrder()
        );
    }

    /**
     * 특정 리뷰 이미지 목록 조회
     */
    @Override
    public List<ReviewImageResponse> getImagesByReview(Integer reviewId) {

        return reviewImageRepository.findByReview_ReviewId(reviewId).stream()
                .map(image -> new ReviewImageResponse(
                        image.getImageId(),
                        image.getImageUrl(),
                        image.getSortOrder()
                ))
                .toList();
    }

    /**
     * 리뷰 이미지 삭제
     */
    @Override
    @Transactional
    public void deleteImage(Integer imageId) {

        ReviewImage reviewImage = reviewImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 이미지가 없습니다."));

        reviewImageRepository.delete(reviewImage);
    }
}
package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.service.ReviewImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ReviewImageController
 *
 * 리뷰 이미지 관련 API 처리 컨트롤러
 */
@RestController
@RequestMapping("/reviews/{reviewId}/images")
@RequiredArgsConstructor
public class ReviewImageController {

    /**
     * 리뷰 이미지 서비스
     */
    private final ReviewImageService reviewImageService;

    /**
     * 리뷰 이미지 업로드
     *
     * POST /reviews/{reviewId}/images
     *
     * form-data 예시
     * - file: 업로드할 이미지 파일
     * - sortOrder: 출력 순서(선택)
     */
    @PostMapping
    public ReviewImageResponse uploadImage(@PathVariable Integer reviewId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {

        return reviewImageService.uploadReviewImage(reviewId, file, sortOrder);
    }

    /**
     * 리뷰 이미지 목록 조회
     *
     * GET /reviews/{reviewId}/images
     */
    @GetMapping
    public List<ReviewImageResponse> getImages(@PathVariable Integer reviewId) {
        return reviewImageService.getImagesByReview(reviewId);
    }

    /**
     * 리뷰 이미지 삭제
     */
    @DeleteMapping("/{imageId}")
    public String deleteImage(@PathVariable Integer imageId) {

        reviewImageService.deleteImage(imageId);

        return "이미지가 삭제되었습니다.";
    }
}
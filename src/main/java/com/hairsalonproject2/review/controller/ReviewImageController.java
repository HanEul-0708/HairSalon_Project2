package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.service.ReviewImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/reviews/{reviewId}/images")
@RequiredArgsConstructor
public class ReviewImageController {

 private final ReviewImageService reviewImageService;

 @PostMapping
 public ReviewImageResponse uploadImage(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer reviewId, @RequestParam("file") MultipartFile file, @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
  return reviewImageService.uploadReviewImage(userDetails.getMember().getMemberId(), isAdmin(userDetails), reviewId, file, sortOrder);
 }

 @GetMapping
 public List<ReviewImageResponse> getImages(@PathVariable Integer reviewId) {
  return reviewImageService.getImagesByReview(reviewId);
 }

 @DeleteMapping("/{imageId}")
 public String deleteImage(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer imageId) {
  reviewImageService.deleteImage(userDetails.getMember().getMemberId(), isAdmin(userDetails), imageId);
  return "이미지가 삭제되었습니다.";
 }

 private boolean isAdmin(CustomUserDetails userDetails) {
  return userDetails.getMember().getRole().name().equals("ADMIN");
 }
}

package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ReviewImageResponse
 * <p>
 * 리뷰 이미지 응답 DTO
 */
@Getter
@AllArgsConstructor
public class ReviewImageResponse {

 /**
  * 이미지 ID
  */
 private Integer imageId;

 /**
  * 이미지 접근 경로
  */
 private String imageUrl;

 /**
  * 이미지 출력 순서
  */
 private Integer sortOrder;
}
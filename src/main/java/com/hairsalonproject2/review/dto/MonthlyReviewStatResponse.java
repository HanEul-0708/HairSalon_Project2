package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * MonthlyReviewStatResponse
 * <p>
 * 월별 리뷰 수 / 평균 평점 집계 응답 DTO
 */
@Getter
@Setter
@AllArgsConstructor
public class MonthlyReviewStatResponse {

 /**
  * 연도
  */
 private Integer year;

 /**
  * 월
  */
 private Integer month;

 /**
  * 해당 월 리뷰 수
  */
 private Long reviewCount;

 /**
  * 해당 월 평균 평점
  */
 private Double averageRating;
}
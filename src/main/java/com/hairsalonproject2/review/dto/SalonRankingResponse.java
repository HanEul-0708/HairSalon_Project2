package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * SalonRankingResponse
 *
 * 미용실 랭킹 응답 DTO
 */
@Getter
@Setter
@AllArgsConstructor
public class SalonRankingResponse {

    /**
     * 미용실 ID
     */
    private Integer salonId;

    /**
     * 미용실 이름
     */
    private String salonName;

    /**
     * 평균 평점
     */
    private Double averageRating;

    /**
     * 리뷰 개수
     */
    private Long reviewCount;
}
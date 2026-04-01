package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DesignerRankingResponse
 *
 * 디자이너 랭킹 응답 DTO
 */
@Getter
@AllArgsConstructor
public class DesignerRankingResponse {

    /**
     * 디자이너 ID
     */
    private Integer designerId;

    /**
     * 디자이너 이름
     */
    private String designerName;

    /**
     * 평균 평점
     */
    private Double averageRating;

    /**
     * 리뷰 개수
     */
    private Long reviewCount;
}
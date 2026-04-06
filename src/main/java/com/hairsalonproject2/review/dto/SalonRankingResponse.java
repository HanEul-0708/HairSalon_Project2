package com.hairsalonproject2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 미용실 랭킹 응답 DTO
 */
@Getter
@Setter
@AllArgsConstructor
public class SalonRankingResponse {

    private Integer salonId;
    private String salonName;
    private String address;
    private Double averageRating;
    private Long reviewCount;
}

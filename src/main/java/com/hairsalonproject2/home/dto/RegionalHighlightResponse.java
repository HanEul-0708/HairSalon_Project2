package com.hairsalonproject2.home.dto;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.salon.dto.response.SalonRankResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RegionalHighlightResponse {
    private String city;
    private String district;
    private String label;
    private List<SalonRankResponse> topSalons;
    private List<DesignerRankingResponse> topDesigners;
    private List<HomeReviewCardResponse> recentReviews;
}

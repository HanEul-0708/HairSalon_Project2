package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.common.util.NameMaskingUtils;
import com.hairsalonproject2.home.dto.HomeReviewCardResponse;
import com.hairsalonproject2.home.dto.RegionalHighlightResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

@Cacheable("homeRegionalHighlights")
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int REGION_ROTATION_LIMIT = 3;

    private final ReviewService reviewService;
    private final SalonQueryService salonQueryService;

    @GetMapping("/")
    public String home(Model model) {
        List<RegionalHighlightResponse> regionalHighlights = buildRegionalHighlights();
        RegionalHighlightResponse initialHighlight = regionalHighlights.get(0);

        model.addAttribute("regionalHighlights", regionalHighlights);
        model.addAttribute("currentRegionLabel", initialHighlight.getLabel());
        model.addAttribute("topSalons", initialHighlight.getTopSalons());
        model.addAttribute("topDesigners", initialHighlight.getTopDesigners());
        model.addAttribute("recentReviews", initialHighlight.getRecentReviews());
        return "index";
    }

    private List<RegionalHighlightResponse> buildRegionalHighlights() {
        List<String> addresses = salonQueryService.getAddressOptions();

        List<RegionalHighlightResponse> highlights = addresses.stream()
                .map(AddressRegionUtils::parse)
                .filter(parts -> !parts.city().isBlank() && !parts.district().isBlank())
                .map(parts -> new RegionKey(parts.city(), parts.district()))
                .distinct()
                .sorted((left, right) -> {
                    int cityCompare = left.city.compareToIgnoreCase(right.city);
                    if (cityCompare != 0) {
                        return cityCompare;
                    }
                    return left.district.compareToIgnoreCase(right.district);
                })
                .limit(REGION_ROTATION_LIMIT)
                .map(region -> RegionalHighlightResponse.builder()
                        .city(region.city)
                        .district(region.district)
                        .label(AddressRegionUtils.combine(region.city, region.district, null))
                        .topSalons(salonQueryService.recommendedTop3(region.city, region.district, null))
                        .topDesigners(reviewService.getTop3Designers(region.city, region.district, null))
                        .recentReviews(toHomeReviews(reviewService.getRecentReviews(region.city, region.district, null)))
                        .build())
                .filter(region -> !region.getTopSalons().isEmpty()
                        || !region.getTopDesigners().isEmpty()
                        || !region.getRecentReviews().isEmpty())
                .toList();

        if (!highlights.isEmpty()) {
            return highlights;
        }

        return List.of(RegionalHighlightResponse.builder()
                .city("")
                .district("")
                .label("전국")
                .topSalons(salonQueryService.recommendedTop3())
                .topDesigners(reviewService.getTop3Designers())
                .recentReviews(toHomeReviews(reviewService.getRecentReviews()))
                .build());
    }

    private List<HomeReviewCardResponse> toHomeReviews(List<ReviewResponse> reviews) {
        return reviews.stream()
                .map(review -> HomeReviewCardResponse.builder()
                        .reviewId(review.getReviewId())
                        .maskedMemberName(NameMaskingUtils.maskName(review.getMemberName()))
                        .salonName(review.getSalonName())
                        .serviceName(review.getServiceName())
                        .rating(review.getRating())
                        .content(review.getContent())
                        .createdDate(review.getCreatedAt() == null ? "" : review.getCreatedAt().toLocalDate().toString().replace('-', '.'))
                        .build())
                .toList();
    }

    private record RegionKey(String city, String district) {
    }
}

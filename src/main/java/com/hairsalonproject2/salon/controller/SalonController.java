package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.support.FilterPageState;
import com.hairsalonproject2.common.support.PageUtils;
import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonMapResultsPayload;
import com.hairsalonproject2.salon.dto.response.SalonRecommendationConditionResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonMapService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {

    private static final int SALONS_PER_PAGE = 9;

    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;
    private final SalonMapService salonMapService;

    @Value("${kakao.javascript-key:}")
    private String kakaoJavascriptKey;

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    @Value("${app.asset-version}")
    private String assetVersion;

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SalonCreateRequest());
        return "salon/form";
    }

    @GetMapping
    public String list(@ModelAttribute SalonSearchRequest request,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        FilterPageState pageState = FilterPageState.of(request.isSearched(), hasSalonFilter(request));
        Page<?> resultPage;

        if (pageState.filterRequired()) {
            resultPage = PageUtils.empty(SALONS_PER_PAGE);
        } else if (pageState.defaultListing()) {
            resultPage = PageUtils.sliceZeroBased(
                    salonQueryService.search(defaultSalonSearchRequest(), 0, SALONS_PER_PAGE).getContent(),
                    0,
                    SALONS_PER_PAGE
            );
        } else {
            resultPage = salonQueryService.search(request, page - 1, SALONS_PER_PAGE);
        }

        model.addAttribute("salons", resultPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", pageState.searched());
        model.addAttribute("defaultListing", pageState.defaultListing());
        model.addAttribute("filterRequired", pageState.filterRequired());
        model.addAttribute("cityOptions", salonQueryService.getCityOptions());
        model.addAttribute("districtOptions", salonQueryService.getDistrictOptions(request.getCity()));
        model.addAttribute("neighborhoodOptions", salonQueryService.getNeighborhoodOptions(request.getCity(), request.getDistrict()));
        model.addAttribute("regionAddresses", salonQueryService.getAddressOptions());
        model.addAttribute("currentPage", PageUtils.currentPage(resultPage));
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("totalSalonCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", PageUtils.pageNumbers(resultPage));
        return "salon/list";
    }

    @GetMapping("/likes")
    public String likedSalons(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (!isAuthenticated(userDetails)) {
            return "redirect:/members/login";
        }

        model.addAttribute("salons", salonQueryService.getLikedSalons(memberId(userDetails)));
        return "salon/liked-list";
    }

    @GetMapping("/map")
    public String mapSearch(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String region,
                            @RequestParam(required = false) String city,
                            @RequestParam(required = false) String district,
                            @RequestParam(required = false) String neighborhood,
                            Model model) {
        String resolvedCity = city == null ? "" : city.trim();
        String resolvedDistrict = district == null ? "" : district.trim();
        String resolvedNeighborhood = neighborhood == null ? "" : neighborhood.trim();

        if (resolvedCity.isBlank() && region != null && !region.isBlank()) {
            var parts = AddressRegionUtils.parse(region);
            resolvedCity = parts.city();
            resolvedDistrict = parts.district();
            resolvedNeighborhood = parts.neighborhood();
        }

        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("city", resolvedCity);
        model.addAttribute("district", resolvedDistrict);
        model.addAttribute("neighborhood", resolvedNeighborhood);
        model.addAttribute("cityOptions", salonQueryService.getCityOptions());
        model.addAttribute("districtOptions", salonQueryService.getDistrictOptions(resolvedCity));
        model.addAttribute("neighborhoodOptions", salonQueryService.getNeighborhoodOptions(resolvedCity, resolvedDistrict));
        model.addAttribute("regionAddresses", salonQueryService.getAddressOptions());
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey == null ? "" : kakaoJavascriptKey.trim());
        model.addAttribute("kakaoRestApiKey", kakaoRestApiKey == null ? "" : kakaoRestApiKey.trim());
        model.addAttribute("assetVersion", assetVersion);
        return "salon/map-search";
    }

    @GetMapping("/map/results")
    @ResponseBody
    public ResponseEntity<SalonMapResultsPayload> mapResults(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String neighborhood
    ) {
        return ResponseEntity.ok(salonMapService.getMapResults(keyword, region, city, district, neighborhood));
    }

    @GetMapping("/{salonId}")
    public String detail(@PathVariable Integer salonId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        model.addAttribute("recommendationCondition", salonQueryService.getRecommendationCondition(salonId));
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey == null ? "" : kakaoJavascriptKey.trim());
        model.addAttribute("assetVersion", assetVersion);
        model.addAttribute(
                "likedByCurrentUser",
                isAuthenticated(userDetails) && salonQueryService.isLikedByMember(salonId, memberId(userDetails))
        );
        return "salon/detail";
    }

    @GetMapping("/{salonId}/recommendation-condition")
    @ResponseBody
    public SalonRecommendationConditionResponse recommendationCondition(@PathVariable Integer salonId) {
        return salonQueryService.getRecommendationCondition(salonId);
    }

    @GetMapping("/{salonId}/edit")
    public String editForm(@PathVariable Integer salonId, Model model) {
        var detail = salonQueryService.getDetail(salonId);
        SalonUpdateRequest form = new SalonUpdateRequest();
        form.setName(detail.getName());
        form.setAddress(detail.getAddress());
        form.setRoadAddress(detail.getRoadAddress());
        form.setPhone(detail.getPhone());
        form.setDescription(detail.getDescription());
        form.setLatitude(detail.getLatitude());
        form.setLongitude(detail.getLongitude());
        form.setImageUrl(detail.getImageUrl());
        form.setPlaceUrl(detail.getPlaceUrl());
        form.setReservable(detail.getReservable());
        model.addAttribute("salonId", salonId);
        model.addAttribute("form", form);
        return "salon/form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") SalonCreateRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "salon/form";
        }
        Integer salonId = salonQueryService.create(request);
        return "redirect:/salons/" + salonId;
    }

    @PostMapping("/{salonId}/edit")
    public String update(@PathVariable Integer salonId,
                         @ModelAttribute("form") SalonUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("salonId", salonId);
            return "salon/form";
        }
        salonQueryService.update(salonId, request);
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId}")
    public String delete(@PathVariable Integer salonId) {
        salonQueryService.delete(salonId);
        return "redirect:/salons";
    }

    @PostMapping("/{salonId}/likes")
    public String like(@PathVariable Integer salonId,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(userDetails)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = salonQueryService.like(salonId, memberId(userDetails));
        redirectAttributes.addFlashAttribute("message", created ? "좋아요 완료" : "이미 좋아요한 미용실입니다.");
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId}/likes")
    public String unlike(@PathVariable Integer salonId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(userDetails)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = salonQueryService.unlike(salonId, memberId(userDetails));
        redirectAttributes.addFlashAttribute("message", deleted ? "좋아요 취소 완료" : "좋아요 정보가 없습니다.");
        return "redirect:/salons/" + salonId;
    }

    @PostMapping("/sync/kakao")
    public String syncFromKakao(@RequestParam String keyword,
                                @RequestParam(required = false) String region,
                                RedirectAttributes redirectAttributes) {
        int count = externalSalonSyncService.syncFromKakao(keyword, region).size();
        redirectAttributes.addFlashAttribute("message", count + "건의 미용실을 동기화했습니다.");
        return "redirect:/salons?searched=true&keyword=" + keyword;
    }

    private boolean isAuthenticated(CustomUserDetails userDetails) {
        return userDetails != null;
    }

    private String memberId(CustomUserDetails userDetails) {
        return userDetails.getUsername();
    }

    private SalonSearchRequest defaultSalonSearchRequest() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setSort("rating");
        request.setSearched(true);
        return request;
    }

    private boolean hasSalonFilter(SalonSearchRequest request) {
        return hasText(request.getKeyword())
                || hasText(request.getRegion())
                || hasText(request.getCity())
                || hasText(request.getDistrict())
                || hasText(request.getNeighborhood())
                || request.getMinRating() != null
                || request.getLatitude() != null
                || request.getLongitude() != null
                || request.getRadiusKm() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}

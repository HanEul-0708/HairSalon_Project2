package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonMapResultResponse;
import com.hairsalonproject2.salon.dto.response.SalonMapResultsPayload;
import com.hairsalonproject2.salon.dto.response.SalonRecommendationConditionResponse;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/salons")
public class SalonController {

    private static final int SALONS_PER_PAGE = 9;

    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;
    private final SalonRepository salonRepository;

    @Value("${kakao.javascript-key:}")
    private String kakaoJavascriptKey;

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SalonCreateRequest());
        return "salon/form";
    }

    @GetMapping
    public String list(@ModelAttribute SalonSearchRequest request,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        String resolvedRegion = String.join(" ",
                request.getCity() == null ? "" : request.getCity(),
                request.getDistrict() == null ? "" : request.getDistrict(),
                request.getNeighborhood() == null ? "" : request.getNeighborhood()
        ).trim();

        if (request.hasSearchRequest()) {
            externalSalonSyncService.syncFromSearch(request.getKeyword(), resolvedRegion.isBlank() ? request.getRegion() : resolvedRegion);
        }

        Page<?> resultPage = request.hasSearchRequest()
                ? salonQueryService.search(request, page - 1, SALONS_PER_PAGE)
                : new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(0, SALONS_PER_PAGE), 0);

        model.addAttribute("salons", resultPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", request.hasSearchRequest());
        model.addAttribute("cityOptions", salonQueryService.getCityOptions());
        model.addAttribute("districtOptions", salonQueryService.getDistrictOptions(request.getCity()));
        model.addAttribute("neighborhoodOptions", salonQueryService.getNeighborhoodOptions(request.getCity(), request.getDistrict()));
        model.addAttribute("regionAddresses", salonQueryService.getAddressOptions());
        model.addAttribute("currentPage", resultPage.isEmpty() ? 1 : resultPage.getNumber() + 1);
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("totalSalonCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", resultPage.getTotalPages() == 0
                ? java.util.Collections.emptyList()
                : java.util.stream.IntStream.rangeClosed(1, resultPage.getTotalPages()).boxed().toList());
        return "salon/list";
    }

    @GetMapping("/likes")
    public String likedSalons(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/members/login";
        }

        model.addAttribute("salons", salonQueryService.getLikedSalons(authentication.getName()));
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
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeRegion = AddressRegionUtils.combine(city, district, neighborhood);
        if (safeRegion.isBlank()) {
            safeRegion = region == null ? "" : region.trim();
        }

        String queryUsed;
        if (safeKeyword.isBlank() && safeRegion.isBlank()) {
            queryUsed = "";
        } else if (safeKeyword.isBlank()) {
            queryUsed = safeRegion + " 미용실";
        } else if (safeRegion.isBlank()) {
            queryUsed = safeKeyword + " 미용실";
        } else {
            queryUsed = safeRegion + " " + safeKeyword + " 미용실";
        }

        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "카카오 REST API 키가 비어 있습니다. application-secret.properties의 kakao.rest-api-key 설정을 확인하세요."
            );
        }

        List<Integer> savedIds;
        try {
            savedIds = externalSalonSyncService.syncFromKakao(keyword, safeRegion);
        } catch (RuntimeException ex) {
            // 카카오 API 호출 실패 시 500 에러 대신 빈 결과를 반환하되, 원인을 프론트에서 확인할 수 있게 한다.
            String safeMessage = (ex.getMessage() == null ? "" : ex.getMessage())
                    .replaceAll("\\r?\\n", " ")
                    .trim();
            if (safeMessage.length() > 120) {
                safeMessage = safeMessage.substring(0, 120) + "...";
            }
            if (safeMessage.isBlank()) {
                safeMessage = "카카오 REST API 검색 실패";
            }

            log.warn("mapResults failed. keyword='{}', region='{}'", keyword, safeRegion, ex);
            return ResponseEntity.ok(
                    SalonMapResultsPayload.builder()
                            .results(List.of())
                            .debug("카카오 검색어: '" + queryUsed + "'")
                            .error(safeMessage)
                            .build()
            );
        }

        List<SalonMapResultResponse> results = savedIds.stream()
                .map(salonRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(salon -> SalonMapResultResponse.builder()
                        .salonId(salon.getSalonId())
                        .name(salon.getName())
                        .address(salon.getAddress())
                        .roadAddress(salon.getRoadAddress())
                        .placeUrl(salon.getPlaceUrl())
                        .latitude(salon.getLatitude())
                        .longitude(salon.getLongitude())
                        .build())
                .toList();

        return ResponseEntity.ok(
                SalonMapResultsPayload.builder()
                        .results(results)
                        .debug("카카오 검색어: '" + queryUsed + "', 결과 " + results.size() + "건")
                        .build()
        );
    }

    @GetMapping("/{salonId}")
    public String detail(@PathVariable Integer salonId, Authentication authentication, Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        model.addAttribute("recommendationCondition", salonQueryService.getRecommendationCondition(salonId));
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey == null ? "" : kakaoJavascriptKey.trim());
        model.addAttribute(
                "likedByCurrentUser",
                isAuthenticated(authentication) && salonQueryService.isLikedByMember(salonId, authentication.getName())
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
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = salonQueryService.like(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute("message", created ? "좋아요 완료" : "이미 좋아요한 미용실입니다.");
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId}/likes")
    public String unlike(@PathVariable Integer salonId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = salonQueryService.unlike(salonId, authentication.getName());
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

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

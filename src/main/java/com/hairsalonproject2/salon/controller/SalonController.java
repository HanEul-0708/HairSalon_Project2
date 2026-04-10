package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonBranchMarkerResponse;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {
    private static final String SALON_LIST_VIEW = "salon/list";

    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;

    @Value("${kakao.javascript-key:}")
    private String kakaoJavascriptKey;

    @GetMapping("/new")
    public String createForm(Authentication authentication, Model model) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }
        model.addAttribute("form", new SalonCreateRequest());
        return "salon/form";
    }

    @GetMapping
    public String list(@RequestParam(required = false) String view,
                       @RequestParam(required = false) String preset,
                       @RequestParam(required = false) String styleKeyword,
                       @RequestParam MultiValueMap<String, String> params,
                       @ModelAttribute("search") SalonSearchRequest request,
                       Model model) {
        if (view != null && !view.isBlank()) {
            LinkedMultiValueMap<String, String> merged = copyParams(params);
            merged.remove("view");
            return redirectTo("/salons", merged);
        }

        StandardPreset resolvedPreset = StandardPreset.from(preset);
        applyStandardPreset(request, styleKeyword, resolvedPreset);
        populateListPage(model, request, createStandardSpec(resolvedPreset));
        populateKakaoComparison(model, request);
        return SALON_LIST_VIEW;
    }

    @GetMapping("/search")
    public String searchRedirect(@RequestParam MultiValueMap<String, String> params) {
        return redirectTo("/salons", params);
    }

    @GetMapping("/branches")
    public String branchesRedirect(@RequestParam MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> merged = copyParams(params);
        merged.remove("view");
        return redirectTo("/salons", merged);
    }

    @GetMapping("/top-rated")
    public String topRatedRedirect(@RequestParam MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> merged = copyParams(params);
        merged.set("preset", StandardPreset.TOP_RATED.value);
        merged.remove("view");
        setIfBlank(merged, "minRating", "4.0");
        setIfBlank(merged, "sort", "rating");
        return redirectTo("/salons", merged);
    }

    @GetMapping("/recommend-by-service")
    public String recommendByServiceRedirect(@RequestParam MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> merged = copyParams(params);
        String styleKeyword = merged.getFirst("styleKeyword");
        if ((merged.getFirst("keyword") == null || merged.getFirst("keyword").isBlank())
                && styleKeyword != null && !styleKeyword.isBlank()) {
            merged.set("keyword", styleKeyword);
        }
        merged.remove("styleKeyword");
        merged.remove("view");
        merged.set("preset", StandardPreset.RECOMMEND_BY_SERVICE.value);
        setIfBlank(merged, "sort", "rating");
        return redirectTo("/salons", merged);
    }

    @GetMapping("/kakao-search")
    public String kakaoSearchRedirect(@RequestParam MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> merged = copyParams(params);
        merged.remove("view");
        return redirectTo("/salons", merged);
    }

    @GetMapping("/likes")
    public String likedSalons(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/members/login";
        }

        model.addAttribute("salons", salonQueryService.getLikedSalons(authentication.getName()));
        return "salon/liked-list";
    }

    @GetMapping("/price-compare")
    public String priceCompareRedirect(@RequestParam(required = false) String serviceName,
                                       @RequestParam(required = false) String region) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/salon-services/compare");
        if (serviceName != null && !serviceName.isBlank()) {
            builder.queryParam("serviceName", serviceName);
        }
        if (region != null && !region.isBlank()) {
            builder.queryParam("region", region);
        }
        return "redirect:" + builder.toUriString();
    }

    @GetMapping("/{salonId:\\d+}")
    public String detail(@PathVariable Integer salonId, Authentication authentication, Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        model.addAttribute(
                "likedByCurrentUser",
                isAuthenticated(authentication) && salonQueryService.isLikedByMember(salonId, authentication.getName())
        );
        return "salon/detail";
    }

    @GetMapping("/{salonId:\\d+}/edit")
    public String editForm(@PathVariable Integer salonId, Authentication authentication, Model model) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        var detail = salonQueryService.getDetail(salonId);
        SalonUpdateRequest form = new SalonUpdateRequest();
        form.setName(detail.getName());
        form.setAddress(detail.getAddress());
        form.setRoadAddress(detail.getRoadAddress());
        form.setPhone(detail.getPhone());
        form.setDescription(detail.getDescription());
        form.setImageUrl(detail.getImageUrl());
        form.setPlaceUrl(detail.getPlaceUrl());
        form.setReservable(detail.getReservable());
        model.addAttribute("salonId", salonId);
        model.addAttribute("form", form);
        return "salon/form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") SalonCreateRequest request,
                         BindingResult bindingResult,
                         Authentication authentication) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        if (bindingResult.hasErrors()) {
            return "salon/form";
        }

        Integer salonId = salonQueryService.create(request);
        return "redirect:/salons/" + salonId;
    }

    @PostMapping("/{salonId:\\d+}/edit")
    public String update(@PathVariable Integer salonId,
                         @ModelAttribute("form") SalonUpdateRequest request,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("salonId", salonId);
            return "salon/form";
        }

        salonQueryService.update(salonId, request);
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId:\\d+}")
    public String delete(@PathVariable Integer salonId, Authentication authentication) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        salonQueryService.delete(salonId);
        return "redirect:/salons";
    }

    @PostMapping("/{salonId:\\d+}/likes")
    public String like(@PathVariable Integer salonId,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = salonQueryService.like(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute(
                "message",
                created ? "좋아요가 반영되었습니다." : "이미 좋아요한 미용실입니다."
        );
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId:\\d+}/likes")
    public String unlike(@PathVariable Integer salonId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = salonQueryService.unlike(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute(
                "message",
                deleted ? "좋아요가 취소되었습니다." : "좋아요 정보가 없습니다."
        );
        return "redirect:/salons/" + salonId;
    }

    @PostMapping("/sync/kakao")
    public String syncFromKakao(@RequestParam String keyword,
                                @RequestParam(required = false) String region,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        if (keyword == null || keyword.isBlank()) {
            redirectAttributes.addFlashAttribute("message", "동기화할 검색어를 입력해 주세요.");
            return buildRedirectToSalonList(keyword, region);
        }

        if (!kakaoLocalSearchClient.isConfigured()) {
            redirectAttributes.addFlashAttribute("message", "카카오 API 키가 설정되지 않아 동기화를 수행할 수 없습니다.");
            return buildRedirectToSalonList(keyword, region);
        }

        try {
            int count = externalSalonSyncService.syncFromKakao(keyword, region).size();
            redirectAttributes.addFlashAttribute(
                    "message",
                    count == 0
                            ? "카카오 검색 결과가 없어 동기화한 미용실이 없습니다."
                            : count + "건의 미용실 데이터를 동기화했습니다."
            );
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("message", "Kakao API 연동에 실패했습니다.");
        }

        return buildRedirectToSalonList(keyword, region);
    }

    private void populateListPage(Model model, SalonSearchRequest request, SalonListPageSpec spec) {
        normalizeSearch(request);
        List<SalonSummaryResponse> salons = salonQueryService.search(request);
        model.addAttribute("salons", salons);
        populateBranchMarkers(model, salons);
        model.addAttribute("search", request);
        model.addAttribute("pageTitle", spec.pageTitle());
        model.addAttribute("pageDescription", spec.pageDescription());
        model.addAttribute("presetTitle", spec.presetTitle());
        model.addAttribute("presetDescription", spec.presetDescription());
        model.addAttribute("searchAction", spec.searchAction());
        model.addAttribute("showPrimarySearch", spec.showPrimarySearch());
        model.addAttribute("primaryKeywordPlaceholder", spec.primaryKeywordPlaceholder());
        model.addAttribute("primarySubmitLabel", spec.primarySubmitLabel());
        model.addAttribute("branchView", spec.branchView());
        model.addAttribute("showKakaoSyncAction", spec.showKakaoSyncAction());
        model.addAttribute("emptyMessage", spec.emptyMessage());
        model.addAttribute("activePreset", spec.activePreset());
        model.addAttribute("kakaoResults", List.of());
        model.addAttribute("kakaoResultsMessage", null);
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        model.addAttribute("kakaoMapConfigured", kakaoLocalSearchClient.isJavascriptConfigured());
    }

    private void populateBranchMarkers(Model model, List<SalonSummaryResponse> salons) {
        if (salons == null || salons.isEmpty()) {
            model.addAttribute("branchMarkers", List.of());
            model.addAttribute("branchMapStatus", "표시할 지점 정보가 없습니다.");
            return;
        }

        if (!kakaoLocalSearchClient.isConfigured()) {
            model.addAttribute("branchMarkers", List.of());
            model.addAttribute("branchMapStatus", "카카오 REST API 키가 설정되지 않아 지도를 표시할 수 없습니다.");
            return;
        }

        List<SalonBranchMarkerResponse> markers = new ArrayList<>();
        int unresolvedCount = 0;

        for (SalonSummaryResponse salon : salons) {
            Optional<SalonBranchMarkerResponse> marker = createBranchMarker(salon);
            if (marker.isPresent()) {
                markers.add(marker.get());
            } else {
                unresolvedCount += 1;
            }
        }

        model.addAttribute("branchMarkers", markers);

        if (markers.isEmpty()) {
            model.addAttribute("branchMapStatus", "좌표를 찾을 수 있는 지점이 없어 지도를 표시할 수 없습니다.");
            return;
        }

        model.addAttribute(
                "branchMapStatus",
                unresolvedCount > 0
                        ? "일부 지점은 좌표를 찾지 못해 지도에서 제외했습니다."
                        : "지점 주소를 기준으로 지도에 위치를 표시합니다."
        );
    }

    private Optional<SalonBranchMarkerResponse> createBranchMarker(SalonSummaryResponse salon) {
        if (salon == null || salon.getSalonId() == null) {
            return Optional.empty();
        }

        Optional<KakaoAddressSearchResult> coordinate = resolveBranchCoordinate(salon);
        if (coordinate.isEmpty()) {
            return Optional.empty();
        }

        KakaoAddressSearchResult result = coordinate.get();
        return Optional.of(SalonBranchMarkerResponse.builder()
                .salonId(salon.getSalonId())
                .name(salon.getName())
                .address(salon.getAddress())
                .roadAddress(salon.getRoadAddress())
                .phone(salon.getPhone())
                .detailUrl("/salons/" + salon.getSalonId())
                .longitude(result.getLongitude())
                .latitude(result.getLatitude())
                .build());
    }

    private Optional<KakaoAddressSearchResult> resolveBranchCoordinate(SalonSummaryResponse salon) {
        Optional<KakaoAddressSearchResult> roadAddressResult = kakaoLocalSearchClient.searchAddress(salon.getRoadAddress());
        if (roadAddressResult.isPresent()) {
            return roadAddressResult;
        }

        return kakaoLocalSearchClient.searchAddress(salon.getAddress());
    }

    private void populateKakaoComparison(Model model, SalonSearchRequest request) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            model.addAttribute("kakaoResultsMessage", "검색어를 입력하면 내부 살롱과 카카오 외부 결과를 함께 비교할 수 있습니다.");
            return;
        }

        if (!kakaoLocalSearchClient.isConfigured()) {
            model.addAttribute("kakaoResultsMessage", "카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다.");
            return;
        }

        model.addAttribute("kakaoResults", kakaoLocalSearchClient.searchSalons(
                request.getKeyword(),
                request.getRegion(),
                1,
                15
        ));
    }

    private void normalizeSearch(SalonSearchRequest request) {
        if (request.getSort() == null || request.getSort().isBlank()) {
            request.setSort("recommended");
        }
    }

    private void applyStandardPreset(SalonSearchRequest request, String styleKeyword, StandardPreset preset) {
        request.setServiceKeywordSearchEnabled(preset == StandardPreset.RECOMMEND_BY_SERVICE);

        if (preset == StandardPreset.TOP_RATED && request.getMinRating() == null) {
            request.setMinRating(BigDecimal.valueOf(4.0));
        }

        if (preset == StandardPreset.RECOMMEND_BY_SERVICE
                && (request.getKeyword() == null || request.getKeyword().isBlank())
                && styleKeyword != null && !styleKeyword.isBlank()) {
            request.setKeyword(styleKeyword);
        }

        if (preset.sort != null && (request.getSort() == null || request.getSort().isBlank())) {
            request.setSort(preset.sort);
        }
    }

    private SalonListPageSpec createStandardSpec(StandardPreset preset) {
        return switch (preset) {
            case TOP_RATED -> new SalonListPageSpec(
                    "살롱 통합 탐색",
                    "목록, 지점 지도, 카카오 외부 검색을 한 화면에서 비교하면서 평점 4점 이상 조건을 적용합니다.",
                    "평점 4점 이상 프리셋",
                    "높은 평점의 내부 살롱과 외부 검색 결과를 같은 화면에서 바로 비교할 수 있습니다.",
                    "/salons",
                    true,
                    "이름, 주소, 설명",
                    "필터 적용",
                    true,
                    true,
                    "평점 4점 이상 조건에 맞는 미용실이 없습니다.",
                    preset.value
            );
            case RECOMMEND_BY_SERVICE -> new SalonListPageSpec(
                    "살롱 통합 탐색",
                    "목록, 지점 지도, 카카오 외부 검색을 한 화면에서 비교하면서 스타일 키워드 추천을 적용합니다.",
                    "스타일 키워드 추천",
                    "시술명 또는 스타일 키워드로 내부 살롱과 외부 검색 결과를 함께 비교합니다.",
                    "/salons",
                    true,
                    "스타일 또는 시술 키워드",
                    "추천 검색",
                    true,
                    true,
                    "입력한 스타일 키워드에 맞는 미용실이 없습니다.",
                    preset.value
            );
            case ALL -> new SalonListPageSpec(
                    "살롱 통합 탐색",
                    "내부 살롱 목록, 지점 안내 지도, 카카오 외부 검색 결과를 한 페이지에서 확인합니다.",
                    null,
                    null,
                    "/salons",
                    true,
                    "이름, 주소, 설명",
                    "통합 검색",
                    true,
                    true,
                    "조건에 맞는 미용실이 없습니다.",
                    preset.value
            );
        };
    }

    private LinkedMultiValueMap<String, String> copyParams(MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> copied = new LinkedMultiValueMap<>();
        params.forEach((key, values) -> copied.put(key, values == null ? List.of() : List.copyOf(values)));
        return copied;
    }

    private void setIfBlank(LinkedMultiValueMap<String, String> params, String key, String value) {
        String current = params.getFirst(key);
        if (current == null || current.isBlank()) {
            params.set(key, value);
        }
    }

    private String redirectTo(String path, MultiValueMap<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach((key, values) -> {
            if (values == null || values.isEmpty()) {
                return;
            }
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> builder.queryParam(key, value));
        });
        return "redirect:" + builder.build().encode().toUriString();
    }

    private String buildRedirectToSalonList(String keyword, String region) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (keyword != null && !keyword.isBlank()) {
            params.add("keyword", keyword);
        }
        if (region != null && !region.isBlank()) {
            params.add("region", region);
        }
        return redirectTo("/salons", params);
    }

    private String requireAdmin(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/members/login";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        return isAdmin ? null : "redirect:/access-denied";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private enum StandardPreset {
        ALL("all", null),
        TOP_RATED("top-rated", "rating"),
        RECOMMEND_BY_SERVICE("recommend-by-service", "rating");

        private final String value;
        private final String sort;

        StandardPreset(String value, String sort) {
            this.value = value;
            this.sort = sort;
        }

        private static StandardPreset from(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }

            for (StandardPreset preset : values()) {
                if (preset.value.equals(value)) {
                    return preset;
                }
            }

            return ALL;
        }
    }

    private record SalonListPageSpec(
            String pageTitle,
            String pageDescription,
            String presetTitle,
            String presetDescription,
            String searchAction,
            boolean showPrimarySearch,
            String primaryKeywordPlaceholder,
            String primarySubmitLabel,
            boolean branchView,
            boolean showKakaoSyncAction,
            String emptyMessage,
            String activePreset
    ) {
    }
}

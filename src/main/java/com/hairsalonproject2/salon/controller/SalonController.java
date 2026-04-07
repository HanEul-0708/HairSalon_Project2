package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
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
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {

    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;

    @Value("${kakao.javascript-key:}")
    private String kakaoJavascriptKey;

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    @GetMapping("/new")
    public String createForm(Authentication authentication, Model model) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }
        model.addAttribute("form", new SalonCreateRequest());
        return "salon/form";
    }

    @GetMapping({"", "/search"})
    public String list(@ModelAttribute("search") SalonSearchRequest request, Model model) {
        populateListPage(model, request, "살롱 목록", "이름, 지역, 평점, 거리 조건으로 미용실을 검색하고 비교할 수 있습니다.");
        return "salon/list";
    }

    @GetMapping("/nearby")
    public String nearby(@ModelAttribute("search") SalonSearchRequest request, Model model) {
        request.setSort("distance");
        if (request.getLatitude() == null || request.getLongitude() == null) {
            model.addAttribute("message", "위도와 경도를 입력하면 가까운 미용실 순으로 정렬됩니다.");
        }
        populateListPage(model, request, "가까운 미용실 찾기", "현재 위치 좌표를 기준으로 가까운 미용실을 찾습니다.");
        return "salon/list";
    }

    @GetMapping("/top-rated")
    public String topRated(@ModelAttribute("search") SalonSearchRequest request, Model model) {
        if (request.getMinRating() == null) {
            request.setMinRating(BigDecimal.valueOf(4.0));
        }
        request.setSort("rating");
        populateListPage(model, request, "평점 4점 이상 미용실", "조건 기반 필터로 높은 평점의 미용실만 확인합니다.");
        return "salon/list";
    }

    @GetMapping("/recommend-by-service")
    public String recommendByService(@RequestParam(required = false) String styleKeyword,
                                     @ModelAttribute("search") SalonSearchRequest request,
                                     Model model) {
        if ((request.getKeyword() == null || request.getKeyword().isBlank())
                && styleKeyword != null && !styleKeyword.isBlank()) {
            request.setKeyword(styleKeyword);
        }
        request.setSort("rating");
        populateListPage(model, request, "스타일 키워드 추천 미용실", "시술명과 스타일 키워드에 맞는 미용실을 평점 순으로 추천합니다.");
        return "salon/list";
    }

    @GetMapping("/kakao-search")
    public String kakaoSearch(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String region,
                              @ModelAttribute("search") SalonSearchRequest request,
                              Model model) {
        populateListPage(model, request, "카카오 미용실 검색", "카카오 API 결과와 내부 저장 미용실 목록을 함께 확인합니다.");
        model.addAttribute("kakaoSearchMode", true);
        model.addAttribute("kakaoKeyword", keyword);
        model.addAttribute("kakaoRegion", region);

        if (keyword == null || keyword.isBlank()) {
            model.addAttribute("kakaoResults", List.of());
            model.addAttribute("message", "검색어를 입력하면 카카오 API로 외부 미용실을 검색할 수 있습니다.");
            return "salon/list";
        }

        if (!kakaoLocalSearchClient.isConfigured()) {
            model.addAttribute("kakaoResults", List.of());
            model.addAttribute("message", "카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다.");
            return "salon/list";
        }

        model.addAttribute("kakaoResults", kakaoLocalSearchClient.searchSalons(keyword, region, 1, 15));
        return "salon/list";
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

    @GetMapping("/map")
    public String map(@ModelAttribute("search") SalonSearchRequest request, Model model) {
        List<SalonSummaryResponse> salons = salonQueryService.search(request)
                .stream()
                .filter(salon -> salon.getLatitude() != null && salon.getLongitude() != null)
                .toList();

        BigDecimal centerLatitude = request.getLatitude();
        BigDecimal centerLongitude = request.getLongitude();

        if ((centerLatitude == null || centerLongitude == null) && !salons.isEmpty()) {
            centerLatitude = salons.get(0).getLatitude();
            centerLongitude = salons.get(0).getLongitude();
        }

        if (centerLatitude == null || centerLongitude == null) {
            centerLatitude = BigDecimal.valueOf(37.5665);
            centerLongitude = BigDecimal.valueOf(126.9780);
        }

        if (salons.isEmpty()) {
            model.addAttribute("message", "좌표가 등록된 미용실이 없어 지도에 표시할 수 없습니다.");
        }

        boolean kakaoMapConfigured = kakaoJavascriptKey != null && !kakaoJavascriptKey.isBlank();
        if (!kakaoMapConfigured) {
            boolean kakaoRestConfigured = kakaoRestApiKey != null && !kakaoRestApiKey.isBlank();
            model.addAttribute("message",
                    kakaoRestConfigured
                            ? "현재 설정된 Kakao REST API 키는 외부 검색과 동기화용이며, 지도 표시에는 Kakao JavaScript 키가 별도로 필요합니다."
                            : "카카오 JavaScript 키가 설정되지 않아 지도를 표시할 수 없습니다.");
        }

        model.addAttribute("salons", salons);
        model.addAttribute("search", request);
        model.addAttribute("pageTitle", "지도에서 미용실 보기");
        model.addAttribute("pageDescription", "좌표가 등록된 미용실을 지도와 목록에서 함께 확인합니다.");
        model.addAttribute("mapCenterLatitude", centerLatitude);
        model.addAttribute("mapCenterLongitude", centerLongitude);
        model.addAttribute("mapDefaultZoom",
                request.getLatitude() != null && request.getLongitude() != null ? 13 : 11);
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        model.addAttribute("kakaoMapConfigured", kakaoMapConfigured);
        return "salon/map";
    }

    @GetMapping("/{salonId}")
    public String detail(@PathVariable Integer salonId, Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        return "salon/detail";
    }

    @GetMapping("/{salonId}/edit")
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

    @PostMapping("/{salonId}/edit")
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

    @DeleteMapping("/{salonId}")
    public String delete(@PathVariable Integer salonId, Authentication authentication) {
        String guard = requireAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        salonQueryService.delete(salonId);
        return "redirect:/salons";
    }

    @PostMapping("/{salonId}/likes")
    public String like(@PathVariable Integer salonId,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = salonQueryService.like(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute("message",
                created ? "좋아요를 반영했습니다." : "이미 좋아요한 미용실입니다.");
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId}/likes")
    public String unlike(@PathVariable Integer salonId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = salonQueryService.unlike(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute("message",
                deleted ? "좋아요를 취소했습니다." : "좋아요 정보가 없습니다.");
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
            return "redirect:/salons";
        }

        if (!kakaoLocalSearchClient.isConfigured()) {
            redirectAttributes.addFlashAttribute("message", "Kakao API 키가 설정되지 않아 동기화를 수행할 수 없습니다.");
            return "redirect:/salons";
        }

        try {
            int count = externalSalonSyncService.syncFromKakao(keyword, region).size();
            redirectAttributes.addFlashAttribute("message",
                    count == 0
                            ? "카카오 검색 결과가 없어 저장된 미용실이 없습니다."
                            : count + "건의 미용실 데이터를 동기화했습니다.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("message", "Kakao API 연동에 실패했습니다.");
        }

        return "redirect:/salons?keyword=" + keyword;
    }

    private void populateListPage(Model model, SalonSearchRequest request, String pageTitle, String pageDescription) {
        model.addAttribute("salons", salonQueryService.search(request));
        model.addAttribute("search", request);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("pageDescription", pageDescription);
    }

    private String requireAdmin(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/members/login";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        return isAdmin ? null : "redirect:/access-denied";
    }
}

package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.common.support.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GlobalSearchController {

    private static final int PREVIEW_SIZE = 4;
    private static final Map<String, String> CITY_TOKEN_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울"),
            Map.entry("서울시", "서울"),
            Map.entry("서울특별시", "서울"),
            Map.entry("부산", "부산"),
            Map.entry("부산시", "부산"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구", "대구"),
            Map.entry("대구시", "대구"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천", "인천"),
            Map.entry("인천시", "인천"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주", "광주"),
            Map.entry("광주시", "광주"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전", "대전"),
            Map.entry("대전시", "대전"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산", "울산"),
            Map.entry("울산시", "울산"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종", "세종"),
            Map.entry("세종시", "세종"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기", "경기"),
            Map.entry("경기도", "경기"),
            Map.entry("강원", "강원"),
            Map.entry("강원도", "강원"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충북", "충북"),
            Map.entry("충청북도", "충북"),
            Map.entry("충남", "충남"),
            Map.entry("충청남도", "충남"),
            Map.entry("전북", "전북"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전남", "전남"),
            Map.entry("전라남도", "전남"),
            Map.entry("경북", "경북"),
            Map.entry("경상북도", "경북"),
            Map.entry("경남", "경남"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주", "제주"),
            Map.entry("제주도", "제주"),
            Map.entry("제주특별자치도", "제주")
    );

    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    private final BoardService boardService;

    @GetMapping("/search")
    public String results(@RequestParam(required = false) String keyword,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return "redirect:/";
        }

        SearchTerms searchTerms = parseSearchTerms(normalizedKeyword);
        var salonPage = salonQueryService.search(buildSalonSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        var designerPage = designerQueryService.search(buildDesignerSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        var servicePage = salonServiceQueryService.list(buildServiceSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        String boardKeyword = boardSearchKeyword(searchTerms, normalizedKeyword);
        var noticePage = boardService.getNoticePage(0, PREVIEW_SIZE, boardKeyword);
        var qnaPage = searchQna(boardKeyword, userDetails);

        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("searchKeyword", searchTerms.keyword());
        model.addAttribute("searchCity", searchTerms.city());
        model.addAttribute("searchDistrict", searchTerms.district());
        model.addAttribute("searchNeighborhood", searchTerms.neighborhood());
        model.addAttribute("salons", salonPage.getContent());
        model.addAttribute("designers", designerPage.getContent());
        model.addAttribute("services", servicePage.getContent());
        model.addAttribute("notices", noticePage.getContent());
        model.addAttribute("qnas", qnaPage.getContent());
        model.addAttribute("salonCount", salonPage.getTotalElements());
        model.addAttribute("designerCount", designerPage.getTotalElements());
        model.addAttribute("serviceCount", servicePage.getTotalElements());
        model.addAttribute("noticeCount", noticePage.getTotalElements());
        model.addAttribute("qnaCount", qnaPage.getTotalElements());
        model.addAttribute("hasAnyResult",
                salonPage.getTotalElements() > 0
                        || designerPage.getTotalElements() > 0
                        || servicePage.getTotalElements() > 0
                        || noticePage.getTotalElements() > 0
                        || qnaPage.getTotalElements() > 0);
        return "search/results";
    }

    private SalonSearchRequest buildSalonSearchRequest(SearchTerms searchTerms) {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSearched(true);
        return request;
    }

    private DesignerSearchRequest buildDesignerSearchRequest(SearchTerms searchTerms) {
        DesignerSearchRequest request = new DesignerSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSearched(true);
        return request;
    }

    private SalonServiceSearchRequest buildServiceSearchRequest(SearchTerms searchTerms) {
        SalonServiceSearchRequest request = new SalonServiceSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSortBy("rating");
        request.setSearched(true);
        return request;
    }

    private SearchTerms parseSearchTerms(String keyword) {
        String city = null;
        String district = null;
        String neighborhood = null;
        List<String> remainingKeywords = new ArrayList<>();

        for (String rawToken : keyword.split("\\s+")) {
            String token = rawToken.trim();
            if (token.isBlank()) {
                continue;
            }

            String normalizedCity = normalizeCityToken(token);
            if (city == null && normalizedCity != null) {
                city = normalizedCity;
                continue;
            }

            if (district == null && isDistrictToken(token)) {
                district = token;
                continue;
            }

            if (neighborhood == null && isNeighborhoodToken(token, city != null || district != null)) {
                neighborhood = token;
                continue;
            }

            remainingKeywords.add(token);
        }

        String parsedKeyword = String.join(" ", remainingKeywords).trim();
        return new SearchTerms(toNullIfBlank(parsedKeyword), city, district, neighborhood);
    }

    private String normalizeCityToken(String token) {
        return CITY_TOKEN_ALIASES.get(token);
    }

    private boolean isDistrictToken(String token) {
        return token.endsWith("구") || token.endsWith("군") || token.endsWith("시");
    }

    private boolean isNeighborhoodToken(String token, boolean hasRegionPrefix) {
        return token.endsWith("동")
                || token.endsWith("읍")
                || token.endsWith("면")
                || (hasRegionPrefix && (token.endsWith("가") || token.endsWith("리")));
    }

    private String toNullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String boardSearchKeyword(SearchTerms searchTerms, String fallbackKeyword) {
        return searchTerms.keyword() == null ? fallbackKeyword : searchTerms.keyword();
    }

    private Page<BoardResponse> searchQna(String keyword, CustomUserDetails userDetails) {
        if (userDetails == null) {
            return PageUtils.empty(PREVIEW_SIZE);
        }

        return boardService.getQnaPage(
                0,
                PREVIEW_SIZE,
                keyword,
                userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
    }

    private boolean canViewAllQna(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_DESIGNER"));
    }

    private record SearchTerms(String keyword, String city, String district, String neighborhood) {
    }
}

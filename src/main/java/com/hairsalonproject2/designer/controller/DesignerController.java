package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 디자이너 공개 페이지 전용 컨트롤러
 *
 * 역할
 * 1. 디자이너 목록 조회
 * 2. 디자이너 상세 조회
 *
 * 주의
 * - 생성/수정/삭제 같은 관리 기능은 AdminDesignerController 로 분리
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/designers")
public class DesignerController {

    private static final int DESIGNERS_PER_PAGE = 9;
    private static final String SEARCH_MODE_AI = "ai";
    private static final String SEARCH_MODE_SEARCH = "search";

    private final DesignerQueryService designerQueryService;
    private final DesignerAiRecommendationService designerAiRecommendationService;

    /**
     * 디자이너 목록 페이지
     * GET /designers
     */
    @GetMapping
    public String list(@ModelAttribute("search") DesignerSearchRequest request,
                       @ModelAttribute("aiRequest") DesignerAiRecommendationRequest aiRequest,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam MultiValueMap<String, String> params,
                       @RequestParam(required = false) String mode,
                       Model model) {
        if (StringUtils.hasText(request.getSortBy())) {
            return redirectToCanonicalList(params);
        }

        String searchMode = resolveSearchMode(mode, aiRequest);
        request.setSearched(true);
        Page<DesignerSummaryResponse> ratingPage = designerQueryService.search(
                copySearchRequest(request, null),
                page - 1,
                DESIGNERS_PER_PAGE
        );
        Page<DesignerSummaryResponse> likesPage = designerQueryService.search(
                copySearchRequest(request, "likes"),
                page - 1,
                DESIGNERS_PER_PAGE
        );
        Page<DesignerSummaryResponse> newestPage = designerQueryService.search(
                copySearchRequest(request, "newest"),
                page - 1,
                DESIGNERS_PER_PAGE
        );

        model.addAttribute("designers", ratingPage.getContent());
        model.addAttribute("designersByRating", ratingPage.getContent());
        model.addAttribute("designersByLikes", likesPage.getContent());
        model.addAttribute("designersByNewest", newestPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", true);
        model.addAttribute("currentPage", ratingPage.isEmpty() ? 1 : ratingPage.getNumber() + 1);
        model.addAttribute("totalPages", ratingPage.getTotalPages());
        model.addAttribute("totalDesignerCount", ratingPage.getTotalElements());
        model.addAttribute("pageNumbers", ratingPage.getTotalPages() == 0
                ? java.util.Collections.emptyList()
                : java.util.stream.IntStream.rangeClosed(1, ratingPage.getTotalPages()).boxed().toList());
        model.addAttribute("searchMode", searchMode);
        model.addAttribute("aiRequest", aiRequest);
        if (SEARCH_MODE_AI.equals(searchMode) && StringUtils.hasText(aiRequest.getQuery())) {
            model.addAttribute("aiResult", designerAiRecommendationService.recommend(aiRequest));
        }
        return "designer/list";
    }

    /**
     * 디자이너 상세 페이지
     * GET /designers/{designerId}
     */
    @GetMapping("/{designerId:\\d+}")
    public String detail(@PathVariable Integer designerId, Authentication authentication, Model model) {
        String loginMemberId = isAuthenticated(authentication) ? authentication.getName() : null;
        model.addAttribute("designer", designerQueryService.getDetail(designerId, loginMemberId));
        return "designer/detail";
    }

    @GetMapping("/likes")
    public String likedDesigners(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/members/login";
        }
        model.addAttribute("designers", designerQueryService.getLikedDesigners(authentication.getName()));
        return "designer/liked-list";
    }

    @PostMapping("/{designerId:\\d+}/likes")
    public String like(@PathVariable Integer designerId,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = designerQueryService.like(designerId, authentication.getName());
        redirectAttributes.addFlashAttribute("message", created ? "좋아요 완료" : "이미 좋아요한 디자이너입니다.");
        return "redirect:/designers/" + designerId;
    }

    @PostMapping("/{designerId:\\d+}/likes/delete")
    public String unlike(@PathVariable Integer designerId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = designerQueryService.unlike(designerId, authentication.getName());
        redirectAttributes.addFlashAttribute("message", deleted ? "좋아요 취소 완료" : "좋아요 정보가 없습니다.");
        return "redirect:/designers/" + designerId;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String redirectToCanonicalList(MultiValueMap<String, String> params) {
        LinkedMultiValueMap<String, String> copied = new LinkedMultiValueMap<>();
        params.forEach((key, values) -> copied.put(key, values == null ? java.util.List.of() : java.util.List.copyOf(values)));
        copied.remove("sortBy");

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/designers");
        copied.forEach((key, values) -> {
            if (values == null || values.isEmpty()) {
                return;
            }
            values.stream()
                    .filter(StringUtils::hasText)
                    .forEach(value -> builder.queryParam(key, value));
        });
        return "redirect:" + builder.build().encode().toUriString();
    }

    private DesignerSearchRequest copySearchRequest(DesignerSearchRequest source, String sortBy) {
        DesignerSearchRequest copied = new DesignerSearchRequest();
        copied.setKeyword(source.getKeyword());
        copied.setSalonKeyword(source.getSalonKeyword());
        copied.setMinRating(source.getMinRating());
        copied.setMinCareerYears(source.getMinCareerYears());
        copied.setMinReviewCount(source.getMinReviewCount());
        copied.setSortBy(sortBy);
        copied.setSearched(source.isSearched());
        return copied;
    }

    private String resolveSearchMode(String mode, DesignerAiRecommendationRequest aiRequest) {
        if (StringUtils.hasText(aiRequest.getQuery())) {
            return SEARCH_MODE_AI;
        }
        if (SEARCH_MODE_AI.equalsIgnoreCase(mode)) {
            return SEARCH_MODE_AI;
        }
        return SEARCH_MODE_SEARCH;
    }
}

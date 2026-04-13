package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.support.FilterPageState;
import com.hairsalonproject2.common.support.PageUtils;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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

    private final DesignerQueryService designerQueryService;
    private final SalonQueryService salonQueryService;

    /**
     * 디자이너 목록 페이지
     * GET /designers
     */
    @GetMapping
    public String list(@ModelAttribute DesignerSearchRequest request,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        FilterPageState pageState = FilterPageState.of(request.isSearched(), hasDesignerFilter(request));
        Page<?> resultPage;

        if (pageState.filterRequired()) {
            resultPage = PageUtils.empty(DESIGNERS_PER_PAGE);
        } else if (pageState.defaultListing()) {
            resultPage = PageUtils.sliceZeroBased(
                    designerQueryService.search(defaultDesignerSearchRequest(), 0, DESIGNERS_PER_PAGE).getContent(),
                    0,
                    DESIGNERS_PER_PAGE
            );
        } else {
            resultPage = designerQueryService.search(request, page - 1, DESIGNERS_PER_PAGE);
        }

        model.addAttribute("designers", resultPage.getContent());
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
        model.addAttribute("totalDesignerCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", PageUtils.pageNumbers(resultPage));
        return "designer/list";
    }

    /**
     * 디자이너 상세 페이지
     * GET /designers/{designerId}
     */
    @GetMapping("/{designerId}")
    public String detail(@PathVariable Integer designerId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        String loginMemberId = isAuthenticated(userDetails) ? memberId(userDetails) : null;
        model.addAttribute("designer", designerQueryService.getDetail(designerId, loginMemberId));
        return "designer/detail";
    }

    @GetMapping("/likes")
    public String likedDesigners(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (!isAuthenticated(userDetails)) {
            return "redirect:/members/login";
        }
        if (userDetails.getMember().getRole() == MemberRole.DESIGNER) {
            model.addAttribute("members", designerQueryService.getMembersWhoLikedDesigner(memberId(userDetails)));
            return "designer/liked-members";
        }

        model.addAttribute("designers", designerQueryService.getLikedDesigners(memberId(userDetails)));
        return "designer/liked-list";
    }

    @PostMapping("/{designerId}/likes")
    public String like(@PathVariable Integer designerId,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(userDetails)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean created = designerQueryService.like(designerId, memberId(userDetails));
        redirectAttributes.addFlashAttribute("message", created ? "좋아요 완료" : "이미 좋아요한 디자이너입니다.");
        return "redirect:/designers/" + designerId;
    }

    @PostMapping("/{designerId}/likes/delete")
    public String unlike(@PathVariable Integer designerId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(userDetails)) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }

        boolean deleted = designerQueryService.unlike(designerId, memberId(userDetails));
        redirectAttributes.addFlashAttribute("message", deleted ? "좋아요 취소 완료" : "좋아요 정보가 없습니다.");
        return "redirect:/designers/" + designerId;
    }

    private boolean isAuthenticated(CustomUserDetails userDetails) {
        return userDetails != null;
    }

    private String memberId(CustomUserDetails userDetails) {
        return userDetails.getUsername();
    }

    private DesignerSearchRequest defaultDesignerSearchRequest() {
        DesignerSearchRequest request = new DesignerSearchRequest();
        request.setSortBy("rating");
        request.setSearched(true);
        return request;
    }

    private boolean hasDesignerFilter(DesignerSearchRequest request) {
        return hasText(request.getKeyword())
                || hasText(request.getSalonKeyword())
                || hasText(request.getCity())
                || hasText(request.getDistrict())
                || hasText(request.getNeighborhood())
                || request.getMinCareerYears() != null
                || request.getMinRating() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        Page<?> resultPage = request.hasSearchRequest()
                ? designerQueryService.search(request, page - 1, DESIGNERS_PER_PAGE)
                : new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(0, DESIGNERS_PER_PAGE), 0);

        model.addAttribute("designers", resultPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", request.hasSearchRequest());
        model.addAttribute("cityOptions", salonQueryService.getCityOptions());
        model.addAttribute("districtOptions", salonQueryService.getDistrictOptions(request.getCity()));
        model.addAttribute("neighborhoodOptions", salonQueryService.getNeighborhoodOptions(request.getCity(), request.getDistrict()));
        model.addAttribute("regionAddresses", salonQueryService.getAddressOptions());
        model.addAttribute("currentPage", resultPage.isEmpty() ? 1 : resultPage.getNumber() + 1);
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("totalDesignerCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", resultPage.getTotalPages() == 0
                ? java.util.Collections.emptyList()
                : java.util.stream.IntStream.rangeClosed(1, resultPage.getTotalPages()).boxed().toList());
        return "designer/list";
    }

    /**
     * 디자이너 상세 페이지
     * GET /designers/{designerId}
     */
    @GetMapping("/{designerId}")
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
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        if (principal.getMember().getRole() == MemberRole.DESIGNER) {
            model.addAttribute("members", designerQueryService.getMembersWhoLikedDesigner(principal.getMember().getMemberId()));
            return "designer/liked-members";
        }

        model.addAttribute("designers", designerQueryService.getLikedDesigners(principal.getMember().getMemberId()));
        return "designer/liked-list";
    }

    @PostMapping("/{designerId}/likes")
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

    @PostMapping("/{designerId}/likes/delete")
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
}

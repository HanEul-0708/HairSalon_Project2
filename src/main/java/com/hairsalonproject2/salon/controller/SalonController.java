package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonRecommendationConditionResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {

    private static final int SALONS_PER_PAGE = 9;

    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SalonCreateRequest());
        return "salon/form";
    }

    @GetMapping
    public String list(@ModelAttribute SalonSearchRequest request,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        Page<?> resultPage = request.hasSearchRequest()
                ? salonQueryService.search(request, page - 1, SALONS_PER_PAGE)
                : new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(0, SALONS_PER_PAGE), 0);

        model.addAttribute("salons", resultPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", request.hasSearchRequest());
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

    @GetMapping("/{salonId}")
    public String detail(@PathVariable Integer salonId, Authentication authentication, Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        model.addAttribute("recommendationCondition", salonQueryService.getRecommendationCondition(salonId));
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

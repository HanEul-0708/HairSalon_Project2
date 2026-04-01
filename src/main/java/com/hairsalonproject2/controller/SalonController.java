package com.hairsalonproject2.controller;

import com.hairsalonproject2.dto.request.SalonCreateRequest;
import com.hairsalonproject2.dto.request.SalonSearchRequest;
import com.hairsalonproject2.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.service.ExternalSalonSyncService;
import com.hairsalonproject2.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {
    private final SalonQueryService salonQueryService;
    private final ExternalSalonSyncService externalSalonSyncService;

    @GetMapping
    public String list(@ModelAttribute SalonSearchRequest request, Model model) {
        model.addAttribute("salons", salonQueryService.search(request));
        model.addAttribute("search", request);
        return "salon/list";
    }

    @GetMapping("/{salonId}")
    public String detail(@PathVariable Integer salonId, Model model) {
        model.addAttribute("salon", salonQueryService.getDetail(salonId));
        return "salon/detail";
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
    public String create(@ModelAttribute("form") SalonCreateRequest request,
                         BindingResult bindingResult) {
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
    public String like(@PathVariable Integer salonId, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }
        boolean created = salonQueryService.like(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute("message", created ? "찜 완료" : "이미 찜한 살롱입니다.");
        return "redirect:/salons/" + salonId;
    }

    @DeleteMapping("/{salonId}/likes")
    public String unlike(@PathVariable Integer salonId, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
            return "redirect:/members/login";
        }
        boolean deleted = salonQueryService.unlike(salonId, authentication.getName());
        redirectAttributes.addFlashAttribute("message", deleted ? "찜 취소 완료" : "찜 정보가 없습니다.");
        return "redirect:/salons/" + salonId;
    }

    @PostMapping("/sync/kakao")
    public String syncFromKakao(@RequestParam String keyword,
                                @RequestParam(required = false) String region,
                                RedirectAttributes redirectAttributes) {
        int count = externalSalonSyncService.syncFromKakao(keyword, region).size();
        redirectAttributes.addFlashAttribute("message", count + "건의 살롱을 동기화했습니다.");
        return "redirect:/salons?keyword=" + keyword;
    }
}

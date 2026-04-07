package com.hairsalonproject2.salonservice.controller;

import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/salon-services")
public class ServiceController {
    private final SalonServiceQueryService salonServiceQueryService;

    @GetMapping("/new")
    public String createForm(Authentication authentication, Model model) {
        String guard = requireDesignerOrAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        model.addAttribute("form", new SalonServiceCreateRequest());
        return "service/form";
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("services", salonServiceQueryService.list(keyword));
        model.addAttribute("keyword", keyword);
        return "service/list";
    }

    @GetMapping("/{serviceId}")
    public String detail(@PathVariable Integer serviceId, Model model) {
        model.addAttribute("serviceItem", salonServiceQueryService.getDetail(serviceId));
        return "service/detail";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam(required = false) String serviceName,
                          @RequestParam(required = false) String region,
                          Model model) {
        model.addAttribute("comparisons", salonServiceQueryService.compare(serviceName, region));
        model.addAttribute("serviceName", serviceName);
        model.addAttribute("region", region);
        return "service/compare";
    }

    @PostMapping
    public String create(@ModelAttribute("form") SalonServiceCreateRequest request,
                         BindingResult bindingResult,
                         Authentication authentication) {
        String guard = requireDesignerOrAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        if (bindingResult.hasErrors()) {
            return "service/form";
        }

        Integer serviceId = salonServiceQueryService.create(request);
        return "redirect:/salon-services/" + serviceId;
    }

    @GetMapping("/{serviceId}/edit")
    public String editForm(@PathVariable Integer serviceId, Authentication authentication, Model model) {
        String guard = requireDesignerOrAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        var detail = salonServiceQueryService.getDetail(serviceId);
        SalonServiceUpdateRequest form = new SalonServiceUpdateRequest();
        form.setSalonId(detail.getSalonId());
        form.setName(detail.getName());
        form.setPrice(detail.getPrice());
        form.setDuration(detail.getDuration());
        form.setDescription(detail.getDescription());
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("form", form);
        return "service/form";
    }

    @PostMapping("/{serviceId}/edit")
    public String update(@PathVariable Integer serviceId,
                         @ModelAttribute("form") SalonServiceUpdateRequest request,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model) {
        String guard = requireDesignerOrAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("serviceId", serviceId);
            return "service/form";
        }

        salonServiceQueryService.update(serviceId, request);
        return "redirect:/salon-services/" + serviceId;
    }

    @DeleteMapping("/{serviceId}")
    public String delete(@PathVariable Integer serviceId, Authentication authentication) {
        String guard = requireDesignerOrAdmin(authentication);
        if (guard != null) {
            return guard;
        }

        salonServiceQueryService.delete(serviceId);
        return "redirect:/salon-services";
    }

    private String requireDesignerOrAdmin(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/members/login";
        }

        boolean authorized = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_DESIGNER".equals(role));

        return authorized ? null : "redirect:/access-denied";
    }
}

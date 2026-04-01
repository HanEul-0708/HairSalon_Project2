package com.hairsalonproject2.controller;

import com.hairsalonproject2.dto.request.DesignerCreateRequest;
import com.hairsalonproject2.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/designers")
public class DesignerController {
    private final DesignerQueryService designerQueryService;

    @GetMapping
    public String list(@ModelAttribute DesignerSearchRequest request, Model model) {
        model.addAttribute("designers", designerQueryService.search(request));
        model.addAttribute("search", request);
        return "designer/list";
    }

    @GetMapping("/{designerId}")
    public String detail(@PathVariable Integer designerId, Model model) {
        model.addAttribute("designer", designerQueryService.getDetail(designerId));
        return "designer/detail";
    }

    @PostMapping
    public String create(@ModelAttribute("form") DesignerCreateRequest request,
                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "designer/form";
        }
        Integer designerId = designerQueryService.create(request);
        return "redirect:/designers/" + designerId;
    }

    @GetMapping("/{designerId}/edit")
    public String editForm(@PathVariable Integer designerId, Model model) {
        var detail = designerQueryService.getDetail(designerId);
        DesignerUpdateRequest form = new DesignerUpdateRequest();
        form.setSalonId(detail.getSalonId());
        form.setMemberId(detail.getMemberId());
        form.setName(detail.getName());
        form.setProfileImage(detail.getProfileImage());
        form.setIntroduction(detail.getIntroduction());
        form.setCareerYears(detail.getCareerYears());
        model.addAttribute("designerId", designerId);
        model.addAttribute("form", form);
        return "designer/form";
    }

    @PostMapping("/{designerId}/edit")
    public String update(@PathVariable Integer designerId,
                         @ModelAttribute("form") DesignerUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("designerId", designerId);
            return "designer/form";
        }
        designerQueryService.update(designerId, request);
        return "redirect:/designers/" + designerId;
    }

    @DeleteMapping("/{designerId}")
    public String delete(@PathVariable Integer designerId) {
        designerQueryService.delete(designerId);
        return "redirect:/designers";
    }
}

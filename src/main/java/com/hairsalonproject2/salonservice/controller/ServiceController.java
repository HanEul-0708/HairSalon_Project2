package com.hairsalonproject2.salonservice.controller;

import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salon-services")
public class ServiceController {
    private static final int SERVICES_PER_PAGE = 8;

    private final SalonServiceQueryService salonServiceQueryService;

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SalonServiceCreateRequest());
        return "service/form";
    }

    @GetMapping
    public String list(@ModelAttribute SalonServiceSearchRequest request,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        request.setSortBy(normalizeSortBy(request.getSortBy()));

        Page<?> resultPage = request.hasSearchRequest()
                ? salonServiceQueryService.list(request, page - 1, SERVICES_PER_PAGE)
                : new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(0, SERVICES_PER_PAGE), 0);

        model.addAttribute("services", resultPage.getContent());
        model.addAttribute("search", request);
        model.addAttribute("searched", request.hasSearchRequest());
        model.addAttribute("currentPage", resultPage.isEmpty() ? 1 : resultPage.getNumber() + 1);
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("totalServiceCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", resultPage.getTotalPages() == 0
                ? java.util.Collections.emptyList()
                : java.util.stream.IntStream.rangeClosed(1, resultPage.getTotalPages()).boxed().toList());
        return "service/list";
    }

    @GetMapping("/{serviceId}")
    public String detail(@PathVariable Integer serviceId, Model model) {
        model.addAttribute("serviceItem", salonServiceQueryService.getDetail(serviceId));
        return "service/detail";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam(required = false) String serviceName, @RequestParam(required = false) String region, Model model) {
        model.addAttribute("comparisons", salonServiceQueryService.compare(serviceName, region));
        model.addAttribute("serviceName", serviceName);
        model.addAttribute("region", region);
        return "service/compare";
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "rating";
        }

        String normalized = sortBy.toLowerCase(Locale.ROOT);
        if ("rating".equals(normalized) || "price".equals(normalized) || "duration".equals(normalized)) {
            return normalized;
        }

        return "rating";
    }

    @PostMapping
    public String create(@ModelAttribute("form") SalonServiceCreateRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "service/form";
        }
        Integer serviceId = salonServiceQueryService.create(request);
        return "redirect:/salon-services/" + serviceId;
    }

    @GetMapping("/{serviceId}/edit")
    public String editForm(@PathVariable Integer serviceId, Model model) {
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
    public String update(@PathVariable Integer serviceId, @ModelAttribute("form") SalonServiceUpdateRequest request, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("serviceId", serviceId);
            return "service/form";
        }
        salonServiceQueryService.update(serviceId, request);
        return "redirect:/salon-services/" + serviceId;
    }

    @PostMapping("/{serviceId}/delete")
    public String delete(@PathVariable Integer serviceId) {
        salonServiceQueryService.delete(serviceId);
        return "redirect:/salon-services";
    }
}

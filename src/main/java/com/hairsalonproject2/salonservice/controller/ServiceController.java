package com.hairsalonproject2.salonservice.controller;

import com.hairsalonproject2.common.support.FilterPageState;
import com.hairsalonproject2.common.support.PageUtils;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final SalonQueryService salonQueryService;

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

        FilterPageState pageState = FilterPageState.of(request.isSearched(), hasServiceFilter(request));
        Page<?> resultPage;

        if (pageState.filterRequired()) {
            resultPage = PageUtils.empty(SERVICES_PER_PAGE);
        } else if (pageState.defaultListing()) {
            resultPage = PageUtils.sliceZeroBased(
                    salonServiceQueryService.list(defaultServiceSearchRequest(), 0, SERVICES_PER_PAGE).getContent(),
                    0,
                    SERVICES_PER_PAGE
            );
        } else {
            resultPage = salonServiceQueryService.list(request, page - 1, SERVICES_PER_PAGE);
        }

        model.addAttribute("services", resultPage.getContent());
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
        model.addAttribute("totalServiceCount", resultPage.getTotalElements());
        model.addAttribute("pageNumbers", PageUtils.pageNumbers(resultPage));
        return "service/list";
    }

    @GetMapping("/{serviceId}")
    public String detail(@PathVariable Integer serviceId, Model model) {
        model.addAttribute("serviceItem", salonServiceQueryService.getDetail(serviceId));
        return "service/detail";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam(required = false) String serviceName,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) String district,
                          @RequestParam(required = false) String neighborhood,
                          Model model) {
        String selectedServiceName = clean(serviceName);
        String selectedCity = clean(city);
        String selectedDistrict = clean(district);
        String selectedNeighborhood = clean(neighborhood);
        boolean filterRequired = !hasCompareFilter(selectedServiceName, selectedCity, selectedDistrict, selectedNeighborhood);

        model.addAttribute("comparisons", filterRequired
                ? java.util.List.of()
                : salonServiceQueryService.compare(selectedServiceName, selectedCity, selectedDistrict, selectedNeighborhood));
        model.addAttribute("cityOptions", salonQueryService.getCityOptions());
        model.addAttribute("districtOptions", salonQueryService.getDistrictOptions(selectedCity));
        model.addAttribute("neighborhoodOptions", salonQueryService.getNeighborhoodOptions(selectedCity, selectedDistrict));
        model.addAttribute("regionAddresses", salonQueryService.getAddressOptions());
        model.addAttribute("serviceNameOptions", salonServiceQueryService.getServiceNameOptions());
        model.addAttribute("serviceName", selectedServiceName);
        model.addAttribute("city", selectedCity);
        model.addAttribute("district", selectedDistrict);
        model.addAttribute("neighborhood", selectedNeighborhood);
        model.addAttribute("filterRequired", filterRequired);
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

    private SalonServiceSearchRequest defaultServiceSearchRequest() {
        SalonServiceSearchRequest request = new SalonServiceSearchRequest();
        request.setSortBy("rating");
        request.setSearched(true);
        return request;
    }

    private boolean hasServiceFilter(SalonServiceSearchRequest request) {
        return hasText(request.getKeyword())
                || hasText(request.getSalonKeyword())
                || hasText(request.getRegion())
                || hasText(request.getCity())
                || hasText(request.getDistrict())
                || hasText(request.getNeighborhood())
                || request.getMaxPrice() != null
                || request.getMaxDuration() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasCompareFilter(String serviceName, String city, String district, String neighborhood) {
        return hasText(serviceName)
                || hasText(city)
                || hasText(district)
                || hasText(neighborhood);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

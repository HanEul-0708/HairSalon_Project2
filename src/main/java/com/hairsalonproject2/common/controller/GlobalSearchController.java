package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class GlobalSearchController {

    private static final int PREVIEW_SIZE = 4;

    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;

    @GetMapping("/search")
    public String results(@RequestParam(required = false) String keyword, Model model) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return "redirect:/";
        }

        var salonPage = salonQueryService.search(buildSalonSearchRequest(normalizedKeyword), 0, PREVIEW_SIZE);
        var designerPage = designerQueryService.search(buildDesignerSearchRequest(normalizedKeyword), 0, PREVIEW_SIZE);
        var servicePage = salonServiceQueryService.list(buildServiceSearchRequest(normalizedKeyword), 0, PREVIEW_SIZE);

        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("salons", salonPage.getContent());
        model.addAttribute("designers", designerPage.getContent());
        model.addAttribute("services", servicePage.getContent());
        model.addAttribute("salonCount", salonPage.getTotalElements());
        model.addAttribute("designerCount", designerPage.getTotalElements());
        model.addAttribute("serviceCount", servicePage.getTotalElements());
        model.addAttribute("hasAnyResult",
                salonPage.getTotalElements() > 0 || designerPage.getTotalElements() > 0 || servicePage.getTotalElements() > 0);
        return "search/results";
    }

    private SalonSearchRequest buildSalonSearchRequest(String keyword) {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword(keyword);
        request.setSearched(true);
        return request;
    }

    private DesignerSearchRequest buildDesignerSearchRequest(String keyword) {
        DesignerSearchRequest request = new DesignerSearchRequest();
        request.setKeyword(keyword);
        request.setSearched(true);
        return request;
    }

    private SalonServiceSearchRequest buildServiceSearchRequest(String keyword) {
        SalonServiceSearchRequest request = new SalonServiceSearchRequest();
        request.setKeyword(keyword);
        request.setSortBy("rating");
        request.setSearched(true);
        return request;
    }
}

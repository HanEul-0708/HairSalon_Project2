package com.hairsalonproject2.controller;

import com.hairsalonproject2.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.dto.request.SalonSearchRequest;
import com.hairsalonproject2.service.DesignerQueryService;
import com.hairsalonproject2.service.SalonQueryService;
import com.hairsalonproject2.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminSalonController {
    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    @GetMapping
    public String adminRoot() {
        return "redirect:/admin/salons";
    }
    @GetMapping("/salons")
    public String salons(Model model) {
        model.addAttribute("salons", salonQueryService.search(new SalonSearchRequest()));
        return "admin/salons";
    }

    @GetMapping("/designers")
    public String designers(Model model) {
        model.addAttribute("designers", designerQueryService.search(new DesignerSearchRequest()));
        return "admin/designers";
    }

    @GetMapping("/salon-services")
    public String salonServices(Model model) {
        model.addAttribute("services", salonServiceQueryService.list(null));
        return "admin/salon-services";
    }
}
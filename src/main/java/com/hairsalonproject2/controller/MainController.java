package com.hairsalonproject2.controller;

import com.hairsalonproject2.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MainController {
    private final SalonQueryService salonQueryService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recommendedSalons", salonQueryService.recommendedTop3());
        return "main/index";
    }
}
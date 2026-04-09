package com.hairsalonproject2.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 메인 대시보드 컨트롤러
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    /**
     * 관리자 메인 페이지
     */
    @GetMapping
    public String adminMain(Model model) {
        model.addAttribute("currentMenu", "dashboard");
        return "admin/admin-main";
    }
}

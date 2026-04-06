package com.hairsalonproject2.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 홈 화면 요청 처리 컨트롤러
 */
@Controller
public class HomeController {

    /**
     * 메인 홈 화면 요청
     */
    @GetMapping("/")
    public String home() {
        // templates/index.html 렌더링
        return "index";
    }
}
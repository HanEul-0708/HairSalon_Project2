package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 디자이너 공개 페이지 전용 컨트롤러
 *
 * 역할
 * 1. 디자이너 목록 조회
 * 2. 디자이너 상세 조회
 *
 * 주의
 * - 생성/수정/삭제 같은 관리 기능은 AdminDesignerController 로 분리
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/designers")
public class DesignerController {

    private final DesignerQueryService designerQueryService;

    /**
     * 디자이너 목록 페이지
     * GET /designers
     */
    @GetMapping
    public String list(@ModelAttribute DesignerSearchRequest request, Model model) {
        model.addAttribute("designers", designerQueryService.search(request));
        model.addAttribute("search", request);
        return "designer/list";
    }

    /**
     * 디자이너 상세 페이지
     * GET /designers/{designerId}
     */
    @GetMapping("/{designerId}")
    public String detail(@PathVariable Integer designerId, Model model) {
        model.addAttribute("designer", designerQueryService.getDetail(designerId));
        return "designer/detail";
    }
}
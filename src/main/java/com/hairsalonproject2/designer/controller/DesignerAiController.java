package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
@RequestMapping("/designers/ai")
public class DesignerAiController {
    private final DesignerAiRecommendationService designerAiRecommendationService;

    @GetMapping
    public String page(@ModelAttribute DesignerAiRecommendationRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/designers");
        builder.queryParam("mode", "ai");
        if (StringUtils.hasText(request.getQuery())) {
            builder.queryParam("query", request.getQuery());
            if (request.getLimit() != null) {
                builder.queryParam("limit", request.getLimit());
            }
        }
        return "redirect:" + builder.build().encode().toUriString();
    }

    @GetMapping("/recommendations")
    @ResponseBody
    public DesignerAiRecommendationResponse recommendations(@ModelAttribute DesignerAiRecommendationRequest request) {
        return designerAiRecommendationService.recommend(request);
    }
}

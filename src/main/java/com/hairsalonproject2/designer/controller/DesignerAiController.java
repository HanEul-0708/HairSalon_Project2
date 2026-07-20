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
  builder.queryParam("mode", "ai"); if (StringUtils.hasText(request.getQuery())) {
   builder.queryParam("query", request.getQuery()); if (request.getLimit() != null) builder.queryParam("limit", request.getLimit());
  } return "redirect:" + builder.build().encode().toUriString();
 }//기능: /designers/ai 경로로 인입되는 GET 요청을 처리하여, AI 검색 조건이 반영된 디자이너 목록 페이지(/designers)로 리다이렉트합니다. 주요 특징 및 구현 내용:동적 쿼리 스트링 생성: UriComponentsBuilder를 활용하여 공통 경로를 지정하고 상황에 맞는 파라미터(mode, query, limit)를 동적으로 결합하여 리다이렉트 URI를 생성합니다. 파라미터 유효성 검증: StringUtils.hasText()를 통해 사용자 검색어(query)의 존재 여부를 검증한 후, 값이 존재하는 경우에만 쿼리 스트링에 안전하게 결합하도록 제어했습니다. URL 안정성 확보: 생성된 URI 스트링을 encode() 처리하여 한글 검색어나 특수문자가 포함되었을 때 발생할 수 있는 브라우저 인코딩 오류 및 깨짐 현상을 방지했습니다.

 @GetMapping("/recommendations")
 @ResponseBody
 public DesignerAiRecommendationResponse recommendations(@ModelAttribute DesignerAiRecommendationRequest request) {
  return designerAiRecommendationService.recommend(request);
 }
}

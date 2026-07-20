package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.designer.dto.request.DesignerCreateRequest;
import com.hairsalonproject2.designer.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 디자이너 관리 컨트롤러
 * <p>
 * 역할
 * 1. 디자이너 생성
 * 2. 디자이너 수정 폼 조회
 * 3. 디자이너 수정
 * 4. 디자이너 삭제
 * <p>
 * 주의
 * - URL 을 /admin/** 아래로 모아서 Security 에서 ADMIN 권한으로 쉽게 제어
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/designers")
public class AdminDesignerController {

 private final DesignerQueryService designerQueryService;

 /**
  * 디자이너 생성
  * POST /admin/designers
  */
 @PostMapping
 public String create(@ModelAttribute("form") DesignerCreateRequest request, BindingResult bindingResult) {
  if (bindingResult.hasErrors()) {
   return "designer/form";
  }
  Integer designerId = designerQueryService.create(request);
  return "redirect:/designers/" + designerId;
 }

 @GetMapping("/new")
 public String createForm(Model model) {
  model.addAttribute("form", new DesignerCreateRequest());
  return "designer/form";
 }

 /**
  * 디자이너 수정 폼
  * GET /admin/designers/{designerId}/edit
  */
 @GetMapping("/{designerId}/edit")
 public String editForm(@PathVariable Integer designerId, Model model) {
  var detail = designerQueryService.getDetail(designerId);
  DesignerUpdateRequest form = new DesignerUpdateRequest();
  form.setSalonId(detail.getSalonId());
  form.setMemberId(detail.getMemberId());
  form.setName(detail.getName());
  form.setProfileImage(detail.getProfileImage());
  form.setIntroduction(detail.getIntroduction());
  form.setCareerYears(detail.getCareerYears());
  model.addAttribute("designerId", designerId);
  model.addAttribute("form", form);
  return "designer/form";
 }

 /**
  * 디자이너 수정
  * POST /admin/designers/{designerId}/edit
  */
 @PostMapping("/{designerId}/edit")
 public String update(@PathVariable Integer designerId, @ModelAttribute("form") DesignerUpdateRequest request, BindingResult bindingResult, Model model) {
  if (bindingResult.hasErrors()) {
   model.addAttribute("designerId", designerId);
   return "designer/form";
  }
  designerQueryService.update(designerId, request);
  return "redirect:/designers/" + designerId;
 }

 /**
  * 디자이너 삭제
  * POST /admin/designers/{designerId}/delete
  * <p>
  * 참고
  * - HTML form 에서 처리하기 쉽게 DELETE 대신 POST 사용
  */
 @PostMapping("/{designerId}/delete")
 public String delete(@PathVariable Integer designerId) {
  designerQueryService.delete(designerId);
  return "redirect:/designers";
 }
}
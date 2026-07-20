package com.hairsalonproject2.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공통 에러 페이지 컨트롤러
 */
@Controller
public class ErrorPageController {

 /**
  * 403 접근 거부 페이지
  */
 @GetMapping("/access-denied")
 public String accessDeniedPage() {
  return "error/access-denied";
 }
}
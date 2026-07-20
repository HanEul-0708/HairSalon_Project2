package com.hairsalonproject2.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {


 @GetMapping("/review/list")
 public String reviewList() {
  return "review/list";
 }

 @GetMapping("/review/write")
 public String reviewWrite() {
  return "review/write";
 }

 @GetMapping("/reservation/list")
 public String reservationList() {
  return "reservation/list";
 }

 @GetMapping("/reservation/create")
 public String reservationCreate() {
  return "reservation/create";
 }
}
package com.hairsalonproject2.board.service;

import com.hairsalonproject2.common.constant.BoardType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class BoardViewGuard {

 private static final String VIEW_COOKIE_NAME = "board_view_history";
 private static final int VIEW_COOKIE_MAX_AGE = 60 * 60 * 24;
 private static final int MAX_VIEW_HISTORY_COUNT = 50;

 public boolean shouldIncreaseView(Integer boardId, BoardType boardType, HttpServletRequest request, HttpServletResponse response) {
  if (alreadyViewed(boardId, boardType, request)) {
   return false;
  }
  appendViewCookie(boardId, boardType, request, response);
  return true;
 }

 private boolean alreadyViewed(Integer boardId, BoardType boardType, HttpServletRequest request) {
  Cookie[] cookies = request.getCookies();
  if (cookies == null) {
   return false;
  }
  String target = makeBoardCookieToken(boardId, boardType);
  for (Cookie cookie : cookies) {
   if (VIEW_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null) {
	String decodedValue = java.net.URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
	for (String token : decodedValue.split("\\|")) {
	 if (target.equals(token)) {
	  return true;
	 }
	}
   }
  }
  return false;
 }

 private void appendViewCookie(Integer boardId, BoardType boardType, HttpServletRequest request, HttpServletResponse response) {
  String target = makeBoardCookieToken(boardId, boardType);
  List<String> tokens = new ArrayList<>();
  tokens.add(target);
  Cookie[] cookies = request.getCookies();
  if (cookies != null) {
   for (Cookie cookie : cookies) {
	if (VIEW_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null) {
	 String decodedValue = java.net.URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
	 if (!decodedValue.isBlank()) {
	  tokens.addAll(Arrays.stream(decodedValue.split("\\|")).filter(token -> !token.isBlank()).filter(token -> !target.equals(token)).toList());
	 }
	}
   }
  }
  if (tokens.size() > MAX_VIEW_HISTORY_COUNT) {
   tokens = new ArrayList<>(tokens.subList(0, MAX_VIEW_HISTORY_COUNT));
  }
  String encodedValue = URLEncoder.encode(String.join("|", tokens), StandardCharsets.UTF_8);
  Cookie cookie = new Cookie(VIEW_COOKIE_NAME, encodedValue);
  cookie.setHttpOnly(true);
  cookie.setPath("/");
  cookie.setMaxAge(VIEW_COOKIE_MAX_AGE);
  response.addCookie(cookie);
 }

 private String makeBoardCookieToken(Integer boardId, BoardType boardType) {
  return boardType.name() + "_" + boardId;
 }
}

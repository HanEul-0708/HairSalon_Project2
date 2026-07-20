package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.designer.dto.request.DesignerAiRecommendationRequest;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 디자이너 공개 페이지 전용 컨트롤러
 * <p>
 * 역할
 * 1. 디자이너 목록 조회
 * 2. 디자이너 상세 조회
 * <p>
 * 주의
 * - 생성/수정/삭제 같은 관리 기능은 AdminDesignerController 로 분리
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/designers")
public class DesignerController {

 private static final int DESIGNERS_PER_PAGE = 9;
 private static final String SEARCH_MODE_AI = "ai";
 private static final String SEARCH_MODE_SEARCH = "search";

 private final DesignerQueryService designerQueryService;
 private final DesignerAiRecommendationService designerAiRecommendationService;

 /**
  * 디자이너 목록 페이지
  * GET /designers
  */
 @GetMapping
 public String list(@ModelAttribute("search") DesignerSearchRequest request, @ModelAttribute("aiRequest") DesignerAiRecommendationRequest aiRequest, @RequestParam(defaultValue = "1") int page, @RequestParam MultiValueMap<String, String> params, @RequestParam(required = false) String mode, Model model) {
  if (StringUtils.hasText(request.getSortBy())) return redirectToCanonicalList(params);
  String searchMode = resolveSearchMode(mode, aiRequest); request.setSearched(true);
  Page<DesignerSummaryResponse> ratingPage = designerQueryService.search(copySearchRequest(request, null), page - 1, DESIGNERS_PER_PAGE);
  Page<DesignerSummaryResponse> likesPage = designerQueryService.search(copySearchRequest(request, "likes"), page - 1, DESIGNERS_PER_PAGE);
  Page<DesignerSummaryResponse> newestPage = designerQueryService.search(copySearchRequest(request, "newest"), page - 1, DESIGNERS_PER_PAGE);
  model.addAttribute("designers", ratingPage.getContent());
  model.addAttribute("designersByRating", ratingPage.getContent());
  model.addAttribute("designersByLikes", likesPage.getContent());
  model.addAttribute("designersByNewest", newestPage.getContent());
  model.addAttribute("search", request);
  model.addAttribute("searched", true);
  model.addAttribute("currentPage", ratingPage.isEmpty() ? 1 : ratingPage.getNumber() + 1);
  model.addAttribute("totalPages", ratingPage.getTotalPages());
  model.addAttribute("totalDesignerCount", ratingPage.getTotalElements());
  model.addAttribute("pageNumbers", ratingPage.getTotalPages() == 0 ? java.util.Collections.emptyList() : java.util.stream.IntStream.rangeClosed(1, ratingPage.getTotalPages()).boxed().toList());
  model.addAttribute("searchMode", searchMode);
  model.addAttribute("aiRequest", aiRequest);
  if (SEARCH_MODE_AI.equals(searchMode) && StringUtils.hasText(aiRequest.getQuery()))
   model.addAttribute("aiResult", designerAiRecommendationService.recommend(aiRequest));
  return "designer/list";//디자이너 목록 페이지의 진입점 역할을 하는 매핑 메소드입니다 (GET /designers). 정렬 기준(sortBy) 파라미터가 명시적으로 들어온 경우 정형화된 URL 구조를 유지하기 위해 redirectToCanonicalList로 리다이렉트 처리를 수행합니다. 동일한 검색 조건에 대하여 평점순(ratingPage), 좋아요순(likesPage), 최신순(newestPage) 데이터를 각각 분리하여 조회한 후 모델에 바인딩합니다. 현재 페이지 번호, 전체 페이지 수, 전체 디자이너 수 등의 페이지네이션 속성을 연산하여 뷰 템플릿으로 전달합니다.
 }

 private String redirectToCanonicalList(MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> copied = new LinkedMultiValueMap<>();
  params.forEach((key, values) -> copied.put(key, values == null ? java.util.List.of() : java.util.List.copyOf(values))); copied.remove("sortBy");
  UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/designers");
  copied.forEach((key, values) -> { if (values == null || values.isEmpty()) return;
   values.stream().filter(StringUtils::hasText).forEach(value -> builder.queryParam(key, value));
  }); return "redirect:" + builder.build().encode().toUriString();
 }//파라미터 맵에서 sortBy 항목을 제거한 후, 남은 검색 조건들을 쿼리 스트링으로 다시 인코딩하여 표준 주소 형태로 리다이렉트 경로를 생성하는 내부 메소드입니다.

 private DesignerSearchRequest copySearchRequest(DesignerSearchRequest source, String sortBy) {
  DesignerSearchRequest copied = new DesignerSearchRequest(); copied.setKeyword(source.getKeyword());
  copied.setSalonKeyword(source.getSalonKeyword()); copied.setMinRating(source.getMinRating());
  copied.setMinCareerYears(source.getMinCareerYears());
  copied.setMinReviewCount(source.getMinReviewCount()); copied.setSortBy(sortBy);
  copied.setSearched(source.isSearched()); return copied;
 }//원본 검색 요청 객체의 속성값(키워드, 미용실 키워드, 최소 평점, 최소 경력, 최소 리뷰 수 등)을 유지하면서 정렬 기준(sortBy)만 다르게 적용된 복사본 객체를 생성하여 반환합니다.

 private String resolveSearchMode(String mode, DesignerAiRecommendationRequest aiRequest) {
  if (StringUtils.hasText(aiRequest.getQuery())) return SEARCH_MODE_AI;
  if (SEARCH_MODE_AI.equalsIgnoreCase(mode)) return SEARCH_MODE_AI; return SEARCH_MODE_SEARCH;
 }//요청 매개변수로 전달된 모드 값이나 AI 추천 요청 객체 내의 질문 텍스트(query) 존재 여부를 확인합니다. 질문 텍스트가 유효하거나 모드가 대소문자 구분 없이 "ai"로 지정된 경우 "ai" 모드로 판별하며, 그렇지 않은 경우에는 일반 검색 모드인 "search"를 반환합니다.

 /**
  * 디자이너 상세 페이지
  * GET /designers/{designerId}
  */
 @GetMapping("/{designerId:\\d+}")
 public String detail(@PathVariable Integer designerId, Authentication authentication, Model model) {
  String loginMemberId = isAuthenticated(authentication) ? authentication.getName() : null;
  model.addAttribute("designer", designerQueryService.getDetail(designerId, loginMemberId));
  return "designer/detail";//특정 식별자를 기반으로 디자이너 상세 정보를 보여주는 엔드포인트입니다 (GET /designers/{designerId}). isAuthenticated 메소드를 호출하여 사용자가 현재 로그인된 상태인지 검증합니다. 로그인 상태라면 해당 회원의 ID를 추출하고, 미인증 상태라면 null을 설정하여 서비스 레이어에 전달합니다. 조회된 디자이너 데이터 객체를 모델에 주입하여 상세 화면 뷰를 반환합니다.
 }

 private boolean isAuthenticated(Authentication authentication) {
  return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
 }//Spring Security의 Authentication 객체가 존재하고, 해당 객체가 익명 사용자 토큰(AnonymousAuthenticationToken)이 아닌 실제 인증된 사용자의 토큰인지를 확인하여 논리값(boolean)으로 반환합니다.

 @GetMapping("/likes")
 public String likedDesigners(Authentication authentication, Model model) {
  if (!isAuthenticated(authentication)) return "redirect:/members/login";
  model.addAttribute("designers", designerQueryService.getLikedDesigners(authentication.getName())); return "designer/liked-list";//현재 로그인한 회원이 좋아요를 누른 디자이너들만 모아서 보여주는 페이지 요청을 처리합니다 (GET /designers/likes). 세션이 인증되지 않은 상태인 경우 로그인 페이지로 강제 리다이렉트하며, 인증된 경우에는 회원 ID를 기반으로 목록을 조회하여 전달합니다.
 }

 @PostMapping("/{designerId:\\d+}/likes")
 public String like(@PathVariable Integer designerId, Authentication authentication, RedirectAttributes redirectAttributes) { if (!isAuthenticated(authentication)) { redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다."); return "redirect:/members/login";
  } boolean created = designerQueryService.like(designerId, authentication.getName());
  redirectAttributes.addFlashAttribute("message", created ? "좋아요 완료" : "이미 좋아요한 디자이너입니다."); return "redirect:/designers/" + designerId;//특정 디자이너에 대해 좋아요 등록 요청을 처리하는 POST 엔드포인트입니다 (POST /designers/{designerId}/likes). 미인증 사용자는 접근할 수 없도록 로그인 페이지로 이동 조치합니다. 서비스 레이어를 통해 정상적으로 좋아요가 반영되었는지 여부를 확인한 후, 그에 맞는 일회성 알림 메시지("좋아요 완료" 또는 "이미 좋아요한 디자이너입니다.")를 구성하여 해당 디자이너의 상세 페이지로 리다이렉트합니다.
 }

 @PostMapping("/{designerId:\\d+}/likes/delete")
 public String unlike(@PathVariable Integer designerId, Authentication authentication, RedirectAttributes redirectAttributes) { if (!isAuthenticated(authentication)) { redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다."); return "redirect:/members/login"; } boolean deleted = designerQueryService.unlike(designerId, authentication.getName()); redirectAttributes.addFlashAttribute("message", deleted ? "좋아요 취소 완료" : "좋아요 정보가 없습니다.");
  return "redirect:/designers/" + designerId;//등록된 디자이너 좋아요를 삭제 및 취소하는 POST 엔드포인트입니다 (POST /designers/{designerId}/likes/delete). 비로그인 상태를 확인하여 차단 조치하며, 정상적으로 삭제가 완료되었는지에 따라 메시지("좋아요 취소 완료" 또는 "좋아요 정보가 없습니다.")를 저장하여 디자이너 상세 페이지로 이동시킵니다.
 }
}

package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReviewPageController.class)
@Import(SecurityConfig.class)
class ReviewPageControllerRenderingTest {

 @Autowired
 private MockMvc mockMvc;

 @MockitoBean
 private ReviewService reviewService;

 @MockitoBean
 private ReservationService reservationService;

 @MockitoBean
 private ReviewRepository reviewRepository;

 @MockitoBean
 private CustomUserDetailsService customUserDetailsService;

 @MockitoBean
 private JpaMetamodelMappingContext jpaMetamodelMappingContext;

 @ParameterizedTest
 @ValueSource(strings = {"", "false"})
 void initialVisitRendersGuideEvenWhenReviewsExist(String searched) throws Exception {
  when(reviewService.getAllReviews(null, null, null, "latest")).thenReturn(reviews(7, false));
  MockHttpServletRequestBuilder request = get("/reviews");
  if (!searched.isEmpty()) request.param("searched", searched);

  Document html = render(request, false, searched.isEmpty() ? "initial" : null);

  assertThat(html.select(".review-empty--guide")).hasSize(1);
  assertThat(html.select(".review-card, #review-results-heading, .catalog-pagination")).isEmpty();
  assertThat(html.select(".review-results-head .review-results-sort")).hasSize(1);
  assertThat(html.select(".review-empty--guide a.btn.btn-primary[href='/reviews?searched=true']")).hasSize(1);
  assertSearchFormContract(html);
 }

 @Test
 void searchedEmptyListRendersResultsAndEmptyMessage() throws Exception {
  when(reviewService.getAllReviews(null, null, null, "latest")).thenReturn(List.of());

  Document html = render(get("/reviews").param("searched", "true"), true, "empty");

  assertThat(html.select(".review-empty--guide, .review-card, .catalog-pagination")).isEmpty();
  assertThat(html.select(".review-results-head h2 span").text()).isEqualTo("0");
  assertThat(html.select(".review-empty")).hasSize(1);
 }

 @ParameterizedTest
 @ValueSource(booleans = {false, true})
 void searchedCardsRenderWithCorrectLikeAccessAndViewerIdentity(boolean loggedIn) throws Exception {
  String memberId = loggedIn ? "reader01" : null;
  when(reviewService.getAllReviews(memberId, "visitor-123", null, "latest")).thenReturn(reviews(1, loggedIn));
  MockHttpServletRequestBuilder request = get("/reviews").param("searched", "true")
    .cookie(new Cookie("review_visitor_token", "visitor-123"));
  if (loggedIn) request.with(authenticationFor(memberId));

  MvcResult result = mockMvc.perform(request).andExpect(status().isOk())
    .andExpect(view().name("review/list")).andExpect(model().attribute("searched", true))
    .andExpect(model().attribute("isLoggedIn", loggedIn)).andReturn();
  Document html = html(result, loggedIn ? "logged-in" : null);

  assertThat(html.select(".review-empty")).isEmpty();
  assertThat(html.select(".review-card")).hasSize(1);
  assertThat(html.select(".review-card__link").attr("href")).isEqualTo("/reviews/1");
  assertThat(html.select(".review-card__content").text()).isEqualTo("Review 1");
  assertThat(html.select(".review-like-button").hasAttr("disabled")).isEqualTo(!loggedIn);
  assertThat(html.select(".review-like-button").hasClass("is-liked")).isEqualTo(loggedIn);
  assertThat(html.select(".review-like-button").attr("aria-pressed")).isEqualTo(Boolean.toString(loggedIn));
  assertThat(html.select(".review-like-button__count").text()).isEqualTo("3");
  verify(reviewService, times(2)).getAllReviews(memberId, "visitor-123", null, "latest");
 }

 @ParameterizedTest
 @ValueSource(strings = {"latest", "rating", "likes"})
 void filteringAndSortingKeepSearchStateAcrossPagination(String sort) throws Exception {
  List<ReviewResponse> matchingReviews = reviews(31, false);
  when(reviewService.getAllReviews(null, null, 41, sort)).thenReturn(matchingReviews);
  when(reviewService.getAllReviews(null, null, null, "latest")).thenReturn(matchingReviews);
  when(reviewService.getAverageRatingByDesigner(41)).thenReturn(4.5);

  MvcResult result = mockMvc.perform(get("/reviews").param("searched", "true")
      .param("designerId", "41").param("sortBy", sort).param("page", "2"))
    .andExpect(status().isOk()).andExpect(view().name("review/list"))
    .andExpect(model().attribute("searched", true)).andExpect(model().attribute("currentPage", 2))
    .andExpect(model().attribute("totalPages", 3)).andExpect(model().attribute("totalReviewCount", 31))
    .andExpect(model().attribute("currentSort", sort)).andExpect(model().attribute("selectedDesignerId", 41))
    .andExpect(model().attribute("selectedDesignerAverage", 4.5)).andReturn();
  Document html = html(result);

  assertThat(html.select(".review-card__link").eachAttr("href"))
    .containsExactlyElementsOf(IntStream.rangeClosed(16, 30).mapToObj(id -> "/reviews/" + id).toList());
  assertThat(html.select("select#sortBy option[selected]").attr("value")).isEqualTo(sort);
  assertThat(html.select("select#designerId option[selected]").attr("value")).isEqualTo("41");
  assertThat(html.select("#review-results-heading .catalog-pagination[aria-label='리뷰 목록 페이지']")).hasSize(1);
  assertThat(html.select(".catalog-pagination [aria-current=page]").text()).isEqualTo("2");
  assertThat(html.select(".catalog-pagination a")).hasSize(2);
  assertThat(html.select(".catalog-pagination a[rel=prev]").attr("href")).contains("page=1");
  assertThat(html.select(".catalog-pagination a[rel=next]").attr("href")).contains("page=3");
  html.select(".catalog-pagination a").forEach(link -> {
   var query = UriComponentsBuilder.fromUriString(link.attr("href")).build().getQueryParams();
   assertThat(query.getFirst("searched")).isEqualTo("true");
   assertThat(query.getFirst("designerId")).isEqualTo("41");
   assertThat(query.getFirst("sortBy")).isEqualTo(sort);
  });
  verify(reviewService).getAllReviews(null, null, 41, sort);
  verify(reviewService).getAllReviews(null, null, null, "latest");
  verify(reviewService).getAverageRatingByDesigner(41);
 }

 @ParameterizedTest
 @CsvSource({"0,1,15,/reviews/1", "-2,1,15,/reviews/1", "99,2,1,/reviews/16"})
 void pageOutsideBoundsRendersNearestValidPage(int requestedPage, int expectedPage, int expectedCards, String firstHref) throws Exception {
  when(reviewService.getAllReviews(null, null, null, "latest")).thenReturn(reviews(16, false));

  MvcResult result = mockMvc.perform(get("/reviews").param("searched", "true")
      .param("page", Integer.toString(requestedPage)))
    .andExpect(status().isOk()).andExpect(model().attribute("searched", true))
    .andExpect(model().attribute("currentPage", expectedPage)).andExpect(model().attribute("totalPages", 2))
    .andReturn();
  Document html = html(result);

  assertThat(html.select(".review-card")).hasSize(expectedCards);
  assertThat(html.select(".review-card__link").first().attr("href")).isEqualTo(firstHref);
  assertThat(html.select(".catalog-pagination [aria-current=page]").text()).isEqualTo(Integer.toString(expectedPage));
  assertThat(html.select(".catalog-pagination [aria-disabled=true]")).hasSize(1);
  assertThat(html.select(".catalog-pagination [aria-disabled=true]").hasAttr("href")).isFalse();
 }

 @ParameterizedTest
 @CsvSource({"1,15,first", "2,15,page2", "3,1,last"})
 void resultPagesUseSharedCatalogStructure(int page, int cards, String fixtureName) throws Exception {
  when(reviewService.getAllReviews(null, null, null, "latest")).thenReturn(reviews(31, false));

  Document html = render(get("/reviews").param("searched", "true").param("page", Integer.toString(page)), true, fixtureName);

  assertSearchFormContract(html);
  assertThat(html.select(".review-card")).hasSize(cards);
  assertThat(html.select(".catalog-results-heading__row .catalog-pagination")).hasSize(1);
  assertThat(html.select(".catalog-pagination__current[aria-current=page]").text()).isEqualTo(Integer.toString(page));
  assertThat(html.select(".catalog-pagination__total").text()).isEqualTo("3");
  assertThat(html.select(".catalog-pagination__button[aria-disabled=true]")).hasSize(page == 2 ? 0 : 1);
  assertThat(html.select(".review-list img")).isNotEmpty();
 }

 private void assertSearchFormContract(Document html) {
  assertThat(html.select("form#review-search-form.review-filter[data-review-auto-search][method=get][action=/reviews]")).hasSize(1);
  assertThat(html.select("#review-search-form input[name=searched][value=true]")).hasSize(1);
  assertThat(html.select("#review-search-form input[name=page]")).isEmpty();
  assertThat(html.select("#review-search-form button[type=submit], #review-search-form input[type=submit]")
    .stream().filter(button -> button.closest("noscript") == null).toList()).isEmpty();
  assertThat(html.select("#review-search-form a.btn.btn-primary.catalog-search-reset[href=/reviews]")).hasSize(1);
  assertThat(html.select("#review-search-form .catalog-select #designerId[name=designerId]")).hasSize(1);
  assertThat(html.select(".review-results-sort .catalog-select #sortBy[name=sortBy][form=review-search-form]")).hasSize(1);
  assertThat(html.select("#review-search-form #sortBy")).isEmpty();
  assertThat(html.select(".review-results-sort button[role=combobox][aria-haspopup=listbox][aria-expanded=false][aria-controls]")).hasSize(1);
  assertThat(html.select(".review-results-sort [role=listbox][hidden]")).hasSize(1);
  assertThat(html.select("#designerId")).hasSize(1);
  assertThat(html.select("#sortBy")).hasSize(1);
 }

 private Document render(MockHttpServletRequestBuilder request, boolean searched, String fixtureName) throws Exception {
  return html(mockMvc.perform(request).andExpect(status().isOk()).andExpect(view().name("review/list"))
    .andExpect(model().attribute("searched", searched)).andReturn(), fixtureName);
 }

 private Document html(MvcResult result) throws Exception {
  return html(result, null);
 }

 private Document html(MvcResult result, String fixtureName) throws Exception {
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  if (fixtureName != null) {
   Path directory = Path.of("build/reviews-catalog/fixtures");
   Files.createDirectories(directory);
   Files.writeString(directory.resolve(fixtureName + ".html"), rendered, StandardCharsets.UTF_8);
  }
  return Jsoup.parse(rendered);
 }

 private List<ReviewResponse> reviews(int count, boolean liked) {
  return IntStream.rangeClosed(1, count).mapToObj(id -> new ReviewResponse(id, id,
    LocalDate.of(2026, 4, 8), LocalTime.of(10, 0), "writer01", id % 3 == 0 ? "긴 이름을 가진 방문 고객" : "Writer", 41,
    "세심한 상담과 스타일을 제안하는 디자이너", "Salon", "커트와 자연스러운 스타일링",
    (byte) 5, id == 1 ? "Review 1" : "상담부터 마무리까지 원하는 분위기를 잘 반영해 주었습니다. ".repeat(6) + id,
    id % 2 == 0 ? "방문해 주셔서 감사합니다. 일상에서도 편하게 관리하실 수 있도록 다음 방문에도 도와드리겠습니다." : null,
    null, LocalDateTime.of(2026, 4, 8, 11, 0), id % 4 == 0 ? null : "/test-assets/review-portrait.svg", 3, liked)).toList();
 }

 private RequestPostProcessor authenticationFor(String memberId) {
  Member member = Member.builder().memberId(memberId).password("encoded-password").name("Reader")
    .phone("010-1234-5678").email(memberId + "@example.com").role(MemberRole.USER).status(MemberStatus.ACTIVE).build();
  CustomUserDetails principal = new CustomUserDetails(member);
  var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
  return SecurityMockMvcRequestPostProcessors.authentication(authentication);
 }
}

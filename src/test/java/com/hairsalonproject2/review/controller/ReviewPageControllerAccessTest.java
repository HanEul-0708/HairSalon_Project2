package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReviewPageController.class)
@Import(SecurityConfig.class)
class ReviewPageControllerAccessTest {

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

 @Test
 void ownerCanOpenReviewWritePage() throws Exception {
  when(reservationService.getReservation(1)).thenReturn(sampleReservation("user01"));
  when(reviewRepository.findByReservation_ReservationId(1)).thenReturn(Optional.empty());
  MvcResult result = mockMvc.perform(get("/reviews/write").with(authenticationFor("user01", MemberRole.USER)).param("reservationId", "1")).andExpect(status().isOk()).andExpect(view().name("review/write")).andReturn();
  var html = Jsoup.parse(writeFixture(result, "write"));
  assertThat(html.select("form#reviewForm[data-mode=create]")).hasSize(1);
  assertThat(html.select("#reservationId").attr("value")).isEqualTo("1");
  assertThat(html.select("#rating").attr("value")).isEqualTo("5");
  assertThat(html.select("#content")).hasSize(1);
  assertThat(html.select("input#image[type=file][accept='image/*']")).hasSize(1);
  assertThat(html.select(".review-reservation-summary").text()).contains("2026-04-08 10:00", "방문완료").doesNotContain("COMPLETED");
  verify(reservationService).validateReservationAccess(1, "user01", false);
 }

 @Test
 void adminCannotOpenOtherMembersReviewWritePage() throws Exception {
  when(reservationService.getReservation(1)).thenReturn(sampleReservation("user01"));
  mockMvc.perform(get("/reviews/write").with(authenticationFor("admin01", MemberRole.ADMIN)).param("reservationId", "1")).andExpect(status().isForbidden());
  verify(reviewRepository, never()).findByReservation_ReservationId(anyInt());
 }

 @Test
 void otherUserCannotOpenReviewEditPage() throws Exception {
  when(reviewService.getReviewDetail(1, "user02", null)).thenReturn(new ReviewDetailResponse(1, 1, LocalDate.of(2026, 4, 8), LocalTime.of(10, 0), "user01", "Writer", 1, "Designer", "Cut", (byte) 5, "Great", null, null, LocalDateTime.of(2026, 4, 8, 11, 0), 0, false, List.of()));
  mockMvc.perform(get("/reviews/1/edit").with(authenticationFor("user02", MemberRole.USER))).andExpect(status().isForbidden()).andExpect(view().name("error/access-denied"));
 }

 @ParameterizedTest
 @ValueSource(booleans = {false, true})
 void reviewDetailRendersImagesReplyAndViewerPermissions(boolean owner) throws Exception {
  String memberId = owner ? "user01" : null;
  when(reviewService.getReviewDetail(1, memberId, null)).thenReturn(sampleReview());
  MockHttpServletRequestBuilder request = get("/reviews/1");
  if (owner) request.with(authenticationFor(memberId, MemberRole.USER));

  MvcResult result = mockMvc.perform(request).andExpect(status().isOk())
    .andExpect(view().name("review/detail")).andExpect(model().attribute("canManageReview", owner))
    .andExpect(model().attribute("isLoggedIn", owner)).andReturn();
  var html = Jsoup.parse(writeFixture(result, owner ? "detail-owner" : "detail-anonymous"));

  assertThat(html.select(".review-detail__content").text()).contains("원하는 분위기");
  assertThat(html.select(".review-card__date").text()).contains("2026-04-08 10:00");
  assertThat(html.select(".review-detail__images img")).hasSize(2);
  assertThat(html.select(".review-card__footer").text()).contains("방문해 주셔서 감사합니다");
  assertThat(html.select(".review-detail__manage")).hasSize(owner ? 1 : 0);
  assertThat(html.select(".review-like-button").hasAttr("disabled")).isFalse();
  assertThat(html.select(".review-like-button").attr("aria-pressed")).isEqualTo("false");
  if (owner) {
   assertThat(html.select("a[href='/reviews/1/edit']")).hasSize(1);
   assertThat(html.select("[data-review-delete][data-review-id='1']")).hasSize(1);
  }
  verify(reviewService).getReviewDetail(1, memberId, null);
 }

 @Test
 void ownerCanEditExistingRatingAndLongContent() throws Exception {
  when(reviewService.getReviewDetail(1, "user01", null)).thenReturn(sampleReview());

  MvcResult result = mockMvc.perform(get("/reviews/1/edit").with(authenticationFor("user01", MemberRole.USER)))
    .andExpect(status().isOk()).andExpect(view().name("review/edit")).andReturn();
  var html = Jsoup.parse(writeFixture(result, "edit"));

  assertThat(html.select("form#reviewForm[data-mode=edit]")).hasSize(1);
  assertThat(html.select("#reviewId").attr("value")).isEqualTo("1");
  assertThat(html.select("#rating").attr("value")).isEqualTo("4");
  assertThat(html.select("textarea#content").first().wholeText()).isEqualTo(sampleReview().getContent());
  assertThat(html.select("a[href='/reviews/1']")).hasSize(1);
 }

 private String writeFixture(MvcResult result, String name) throws Exception {
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  Path directory = Path.of("build/reviews-catalog/fixtures");
  Files.createDirectories(directory);
  Files.writeString(directory.resolve(name + ".html"), rendered, StandardCharsets.UTF_8);
  return rendered;
 }

 private ReviewDetailResponse sampleReview() {
  return new ReviewDetailResponse(1, 1, LocalDate.of(2026, 4, 8), LocalTime.of(10, 0), "user01", "긴 이름을 가진 방문 고객",
    1, "세심한 상담과 스타일을 제안하는 디자이너", "커트와 자연스러운 스타일링", (byte) 4,
    "상담부터 마무리까지 원하는 분위기를 잘 반영해 주었습니다. ".repeat(12),
    "방문해 주셔서 감사합니다. 일상에서도 편하게 관리하실 수 있도록 다음 방문에도 도와드리겠습니다. ".repeat(3),
    LocalDateTime.of(2026, 4, 8, 12, 0), LocalDateTime.of(2026, 4, 8, 11, 0), 3, false,
    List.of(new ReviewImageResponse(1, "/test-assets/review-portrait.svg", 0),
      new ReviewImageResponse(2, "/test-assets/review-landscape.svg", 1)));
 }

 private ReservationResponse sampleReservation(String memberId) {
  return ReservationResponse.builder().reservationId(1).memberId(memberId).designerId(1).designerName("Designer").salonServiceId(1).serviceName("Cut").reservationDate(LocalDate.of(2026, 4, 8)).reservationTime(LocalTime.of(10, 0)).status(ReservationStatus.COMPLETED).createdAt(LocalDateTime.of(2026, 4, 8, 9, 0)).build();
 }

 private RequestPostProcessor authenticationFor(String memberId, MemberRole role) {
  Member member = Member.builder().memberId(memberId).password("encoded-password").name("Tester").phone("010-1234-5678").email(memberId + "@example.com").role(role).status(MemberStatus.ACTIVE).build();
  CustomUserDetails principal = new CustomUserDetails(member);
  Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
  return SecurityMockMvcRequestPostProcessors.authentication(authentication);
 }
}

package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.dto.response.DesignerCatalogPage;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DesignerController.class, DesignerAiController.class})
@Import(SecurityConfig.class)
class DesignerSearchRenderingTest {
 @Autowired private MockMvc mvc;
 @MockitoBean private DesignerQueryService queryService;
 @MockitoBean private DesignerAiRecommendationService aiService;
 @MockitoBean private CustomUserDetailsService userDetailsService;
 @MockitoBean private JpaMetamodelMappingContext metamodel;

 @ParameterizedTest
 @CsvSource({
  "minRating, 문자, 최소 평점은 0부터 5 사이의 숫자로 입력해 주세요.",
  "minRating, 5.1, 최소 평점은 5 이하로 입력해 주세요.",
  "minRating, -0.1, 최소 평점은 0 이상으로 입력해 주세요.",
  "minCareerYears, 2147483648, 최소 경력은 0 이상의 정수로 입력해 주세요.",
  "minCareerYears, -1, 최소 경력은 0 이상으로 입력해 주세요.",
  "minReviewCount, 9223372036854775808, 최소 리뷰 수는 0 이상의 정수로 입력해 주세요.",
  "minReviewCount, -1, 최소 리뷰 수는 0 이상으로 입력해 주세요.",
  "page, 문자, 페이지는 1 이상의 정수로 입력해 주세요.",
  "page, 2147483648, 페이지는 1 이상의 정수로 입력해 주세요.",
  "page, 0, 페이지 번호는 1 이상으로 입력해 주세요."
 })
 void invalidSearchRendersRejectedTextAndKoreanErrorsWithoutQueries(String field, String value, String message) throws Exception {
  var result = mvc.perform(get("/designers").param(field, value).param("keyword", "커트"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attributeHasFieldErrors("search", field)).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var searchForm = html.selectFirst("#designer-search-panel");
  assertThat(searchForm).isNotNull();
  assertThat(searchForm.selectFirst("input[name=" + field + "]").val()).isEqualTo(value);
  assertThat(searchForm.selectFirst("input[name=keyword]").val()).isEqualTo("커트");
  assertThat(html.text()).contains(message).doesNotContain("Failed to convert", "java.lang");
  verifyNoInteractions(queryService, aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"bad", "문자", "2147483648", "1.5", "0", "7", "-1", ""})
 void invalidAiLimitRendersRejectedOptionWithoutQueries(String value) throws Exception {
  var result = mvc.perform(get("/designers").param("mode", "ai").param("query", "염색").param("limit", value))
   .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "limit")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var selected = html.select("select[name=limit] option[selected]");
  assertThat(selected).hasSize(1);
  assertThat(selected.first().val()).isEqualTo(value);
  assertThat(html.selectFirst("textarea[name=query]").val()).isEqualTo("염색");
  assertThat(html.select(".designer-ai-inline-form .invalid-feedback").text()).isNotBlank()
   .doesNotContain("Failed to convert", "java.lang");
  verifyNoInteractions(queryService, aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"1", "2", "3", "4", "5", "6"})
 void validAiLimitSelectsOnlyRequestedOption(String value) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers").param("mode", "ai").param("limit", value))
   .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var selected = html.select("select[name=limit] option[selected]");
  assertThat(selected).hasSize(1);
  assertThat(selected.first().val()).isEqualTo(value);
  assertThat(html.select("select[name=limit] option")).hasSize(6);
  verifyNoInteractions(aiService);
 }

 @Test
 void oversizedAiQueryIsPreservedWithoutQueries() throws Exception {
  String query = "염".repeat(301);
  var result = mvc.perform(get("/designers").param("query", query))
   .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "query")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.selectFirst("textarea[name=query]").val()).isEqualTo(query);
  assertThat(html.select("select[name=limit] option[selected]")).hasSize(1);
  assertThat(html.selectFirst("select[name=limit] option[selected]").val()).isEqualTo("3");
  assertThat(html.text()).contains("추천 요청은 300자 이내로 입력해 주세요.");
  verifyNoInteractions(queryService, aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"문자", "2147483648", "0", "7", ""})
 void authenticatedJsonInvalidLimitReturns400WithoutQueries(String value) throws Exception {
  mvc.perform(get("/designers/ai/recommendations").with(user("member")).param("query", "염색").param("limit", value))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("limit"));
  verifyNoInteractions(queryService, aiService);
 }

 @Test
 void authenticatedJsonOversizedQueryReturns400WithoutQueries() throws Exception {
  mvc.perform(get("/designers/ai/recommendations").with(user("member")).param("query", "염".repeat(301)))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("query"));
  verifyNoInteractions(queryService, aiService);
 }

 @Test
 void recommendationsKeepExistingAuthenticationRequirement() throws Exception {
  mvc.perform(get("/designers/ai/recommendations").param("query", "염색").param("limit", "bad"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/members/login"));
  verifyNoInteractions(queryService, aiService);
 }

 @Test
 void aiAliasPreservesInvalidTextForCanonicalValidation() throws Exception {
  mvc.perform(get("/designers/ai").param("query", "color").param("limit", "invalid"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/designers?mode=ai&query=color&limit=invalid"));
  verifyNoInteractions(queryService, aiService);
 }

 @Test
 void validPublicSearchUsesOneCatalogSnapshot() throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers")).andExpect(status().isOk()).andExpect(view().name("designer/list")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select("select[name=limit] option[selected]")).hasSize(1);
  assertThat(html.selectFirst("select[name=limit] option[selected]").val()).isEqualTo("3");
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoMoreInteractions(queryService);
  verifyNoInteractions(aiService);
 }
}

package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationItemResponse;
import com.hairsalonproject2.designer.dto.response.DesignerAiRecommendationResponse;
import com.hairsalonproject2.designer.dto.response.DesignerCatalogPage;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 @ValueSource(strings = {"search", "ai"})
 void bothModesKeepIndependentFormsWithResetBeforeModeControlsAndInputsInTheirOwnArea(String mode) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers").param("mode", mode))
   .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

  assertSelectedMode(html, mode);
  var forms = html.select("form#designer-search-panel, form.designer-ai-inline-form");
  assertThat(forms).hasSize(2);
  for (var form : forms) {
   assertThat(form.closest("form")).isSameAs(form);
   assertThat(form.parents()).noneMatch(parent -> parent.tagName().equals("form"));
   assertThat(form.select("input[type=hidden][name=mode]")).hasSize(1);
   String formMode = form.hasClass("designer-ai-inline-form") ? "ai" : "search";
   assertThat(form.selectFirst("input[name=mode]").val()).isEqualTo(formMode);
   var layout = form.selectFirst(".designer-form-layout");
   assertThat(layout).isNotNull();
   assertThat(layout.children()).hasSize(2);
   assertThat(layout.child(0).hasClass("designer-form-layout__inputs")).isTrue();
   var controls = layout.child(1);
   assertThat(controls.hasClass("designer-search-form__controls")).isTrue();
   assertThat(controls.children()).hasSize(2);
   assertThat(controls.child(0).select("a.catalog-search-reset")).hasSize(1);
   assertThat(controls.child(0).selectFirst("a.catalog-search-reset").attr("href"))
    .isEqualTo("/designers?mode=" + formMode);
   assertThat(controls.child(1).select("nav.designer-search-mode-switch")).hasSize(1);
   assertThat(controls.select("input, textarea, .invalid-feedback")).isEmpty();
  }

  var aiForm = html.selectFirst("form.designer-ai-inline-form");
  var textarea = aiForm.selectFirst("textarea[name=query]");
  assertThat(textarea.attr("rows")).isEqualTo("4");
  assertThat(textarea.attr("maxlength")).isEqualTo("300");
  assertThat(textarea.closest(".designer-form-layout__inputs")).isNotNull();
  assertThat(aiForm.selectFirst("#designer-ai-validation").closest(".designer-form-layout__inputs")).isNotNull();
  assertThat(aiForm.select(".designer-ai-panel__controls nav.designer-search-mode-switch")).hasSize(1);
  assertThat(aiForm.select("button[type=submit], select[name=limit]")).isEmpty();

  var textFields = html.selectFirst("#designer-search-panel .designer-search-form__text-fields");
  assertThat(textFields).isNotNull();
  assertThat(textFields.select("input[type=text]").eachAttr("name")).containsExactly("keyword", "salonKeyword");
  assertThat(textFields.closest(".designer-form-layout__inputs")).isNotNull();
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoInteractions(aiService);
 }

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
  assertThat(searchForm.selectFirst("input[name=" + field + "]").closest(".designer-form-layout__inputs")).isNotNull();
  assertThat(searchForm.select(".designer-search-form__controls .invalid-feedback")).isEmpty();
  assertThat(html.text()).contains(message).doesNotContain("Failed to convert", "java.lang");
  verifyNoInteractions(queryService, aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"bad", "문자", "2147483648", "1.5", "0", "-1"})
 void invalidLegacyAiLimitRendersAccessibleQueryErrorWithoutSelectOrQueries(String value) throws Exception {
  var result = mvc.perform(get("/designers").param("mode", "ai").param("query", "염색").param("limit", value))
   .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "limit")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select("select[name=limit], #aiLimit")).isEmpty();
  assertThat(html.selectFirst("textarea[name=query]").val()).isEqualTo("염색");
  assertThat(html.select(".designer-ai-inline-form .invalid-feedback").text()).isNotBlank()
   .doesNotContain("Failed to convert", "java.lang");
  assertThat(html.select("#designer-ai-validation [data-designer-ai-field=query]")).isNotEmpty();
  assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo("true");
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  verifyNoInteractions(queryService, aiService);
  if ("bad".equals(value)) writeAiBrowserFixture("validation.html", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
 }

 @ParameterizedTest
 @ValueSource(strings = {"1", "2", "3", "6", "7", "100", ""})
 void validLegacyAiLimitHasNoSelectionControlOrArtificialCap(String value) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers").param("mode", "ai").param("limit", value))
   .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select("select[name=limit], #aiLimit, .designer-ai-inline-form__side")).isEmpty();
  assertThat(html.selectFirst("#aiQuery").attr("placeholder"))
   .isEqualTo("스타일·조건과 추천 인원(예: 2명, 생략 시 조건에 맞는 전원)을 입력하세요.");
  assertThat(html.select(".designer-ai-inline-form button[type=submit]")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-panel").hasAttr("hidden")).isFalse();
  assertThat(html.selectFirst("#designer-ai-validation").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-results-area").attr("aria-busy")).isEqualTo("false");
  assertThat(html.selectFirst("#designer-ai-status").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-complete")).isEqualTo("false");
  verifyNoInteractions(aiService);
  if ("3".equals(value)) writeAiBrowserFixture("empty.html", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
 }

 @Test
 void aiFullPageDefersRecommendationsUntilBrowserRestoresActiveMode() throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers").param("mode", "ai").param("query", "염색").param("limit", "3"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.select(".designer-ai-inline-form button[type=submit]")).isEmpty();
  assertThat(html.selectFirst(".designer-ai-inline-form").attr("action")).isEqualTo("/designers");
  assertSelectedMode(html, "ai");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo("염색");
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-complete")).isEqualTo("false");
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-query")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("data-limit")).isFalse();
  assertThat(html.select("#designer-ai-result-content .designer-ai-card")).isEmpty();
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoInteractions(aiService);
  writeAiBrowserFixture("results.html", rendered);
 }

 @ParameterizedTest
 @CsvSource({"ai, 염색", "AI, 염색", "aI, 펌"})
 void publicAiLiveFragmentIgnoresOrdinarySearchErrorsAndSkipsCatalog(String mode, String query) throws Exception {
  when(aiService.recommend(any())).thenReturn(aiResponse(query));
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", mode).param("query", query).param("limit", "3").param("minRating", "bad")
    .param("minCareerYears", "bad").param("page", "bad").param("sortBy", "rating"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list :: designerAiResponse")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.select(".catalog-hero, #designer-search-panel, #designer-results-area")).isEmpty();
  assertThat(html.select("#designer-ai-panel")).hasSize(1);
  assertThat(html.select(".designer-ai-inline-form button[type=submit]")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-validation").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo("false");
  assertThat(html.select("#aiLimit")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-results-area").hasAttr("hidden")).isFalse();
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isFalse();
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-query")).isEqualTo(query);
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("data-limit")).isFalse();
  assertThat(html.select("#designer-ai-result-content .designer-ai-card")).hasSize(1);
  verifyNoInteractions(queryService);
  verify(aiService).recommend(argThat(request -> query.equals(request.getQuery()) && request.getLimit() == 3));
  verifyNoMoreInteractions(aiService);
  if ("ai".equals(mode)) writeAiBrowserFixture("results-fragment.html", rendered);
 }

 @ParameterizedTest
 @ValueSource(strings = {"", "   "})
 void blankAiLiveFragmentHasStableEmptyRegionsWithoutQueries(String query) throws Exception {
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", query))
   .andExpect(status().isOk()).andExpect(view().name("designer/list :: designerAiResponse")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.select("#designer-ai-results-area")).hasSize(1);
  assertThat(html.selectFirst("#designer-ai-status").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-validation").hasAttr("hidden")).isTrue();
  var content = html.selectFirst("#designer-ai-result-content");
  assertThat(content.hasAttr("hidden")).isTrue();
  assertThat(content.attr("data-complete")).isEqualTo("false");
  assertThat(content.attr("data-query")).isEmpty();
  assertThat(content.hasAttr("data-limit")).isFalse();
  assertThat(content.children()).isEmpty();
  verifyNoInteractions(queryService, aiService);
  if (query.isEmpty()) writeAiBrowserFixture("blank-fragment.html", rendered);
 }

 @Test
 void aiLiveFragmentRendersCompletedEmptyRecommendationsWithoutCatalogQueries() throws Exception {
  when(aiService.recommend(any())).thenReturn(DesignerAiRecommendationResponse.builder().query("특별한 스타일")
   .summary("조건에 맞는 디자이너를 찾지 못했습니다.").source("LOCAL_RULES").llmUsed(false)
   .recommendations(List.of()).build());
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", "특별한 스타일").param("limit", "3"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list :: designerAiResponse")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  var content = html.selectFirst("#designer-ai-result-content");
  assertThat(content.attr("data-complete")).isEqualTo("true");
  assertThat(content.attr("data-query")).isEqualTo("특별한 스타일");
  assertThat(content.hasAttr("hidden")).isFalse();
  assertThat(content.select(".designer-ai-card")).isEmpty();
  assertThat(content.select(".designer-empty-state")).hasSize(1);
  assertThat(content.select(".designer-empty-state").text()).contains("추천할 디자이너를 찾지 못했습니다.");
  assertThat(html.selectFirst("#designer-ai-results-area").hasAttr("hidden")).isFalse();
  verifyNoInteractions(queryService);
  verify(aiService).recommend(argThat(request -> "특별한 스타일".equals(request.getQuery()) && request.getLimit() == 3));
  verifyNoMoreInteractions(aiService);
  writeAiBrowserFixture("empty-results-fragment.html", rendered);
 }

 @ParameterizedTest
 @ValueSource(strings = {"bad", "0", "-1"})
 void invalidAiLiveLimitReturnsMappedValidationWithoutQueries(String limit) throws Exception {
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", "염색").param("limit", limit))
   .andExpect(status().isOk()).andExpect(view().name("designer/list :: designerAiResponse"))
   .andExpect(model().attributeHasFieldErrors("aiRequest", "limit")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.selectFirst("#designer-ai-validation").hasAttr("hidden")).isFalse();
  assertThat(html.select("#designer-ai-validation [data-designer-ai-field=query]").text()).isNotBlank()
   .doesNotContain("Failed to convert", "java.lang");
  assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo("true");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo("염색");
  assertThat(html.select("select[name=limit], #aiLimit")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  verifyNoInteractions(queryService, aiService);
  if ("bad".equals(limit)) writeAiBrowserFixture("invalid-fragment.html", rendered);
 }

 @Test
 void oversizedAiLiveQueryReturnsMappedValidationWithoutQueries() throws Exception {
  String query = "염".repeat(301);
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", query))
   .andExpect(status().isOk()).andExpect(view().name("designer/list :: designerAiResponse"))
   .andExpect(model().attributeHasFieldErrors("aiRequest", "query")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select("#designer-ai-validation [data-designer-ai-field=query]").text())
   .isEqualTo("추천 요청은 300자 이내로 입력해 주세요.");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo(query);
  assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo("true");
  assertThat(html.select("#aiLimit")).isEmpty();
  verifyNoInteractions(queryService, aiService);
 }

 @Test
 void oversizedAiQueryIsPreservedWithoutQueries() throws Exception {
  String query = "염".repeat(301);
  var result = mvc.perform(get("/designers").param("query", query))
   .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "query")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.selectFirst("textarea[name=query]").val()).isEqualTo(query);
  assertThat(html.select("select[name=limit], #aiLimit")).isEmpty();
  assertThat(html.text()).contains("추천 요청은 300자 이내로 입력해 주세요.");
  verifyNoInteractions(queryService, aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"문자", "2147483648", "0", "-1"})
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

 @ParameterizedTest
 @ValueSource(strings = {"search", "SEARCH", "SeArCh"})
 void explicitSearchModeWinsOverRetainedAiQuery(String mode) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15))).thenReturn(catalogPage(0));
  var result = mvc.perform(filteredSearchRequest(mode, 1).param("query", "염색").param("limit", "3"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attribute("searchMode", "search")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertSelectedMode(html, "search");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo("염색");
  assertThat(html.selectFirst("#designer-results-area").attr("data-current-page")).isEqualTo("1");
  assertThat(html.select("#designer-results-area [data-designer-sort-panel=rating] .designer-card")).hasSize(1);
  verify(queryService).searchCatalog(argThat(request -> "커트".equals(request.getKeyword())
   && "스타일".equals(request.getSalonKeyword()) && new BigDecimal("4").compareTo(request.getMinRating()) == 0
   && request.getMinCareerYears() == 5 && request.getMinReviewCount() == 2L), eq(0), eq(15));
  verifyNoInteractions(aiService);
  if ("search".equals(mode)) writeAiBrowserFixture("search.html", rendered);
 }

 @ParameterizedTest
 @NullAndEmptySource
 @ValueSource(strings = {"unknown"})
 void legacyQueryStillSelectsAiWithoutRecognizedMode(String mode) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15))).thenReturn(catalogPage(0));
  var request = get("/designers").param("query", "염색").param("limit", "3");
  if (mode != null) request.param("mode", mode);
  var result = mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attribute("searchMode", "ai")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertSelectedMode(html, "ai");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo("염색");
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-complete")).isEqualTo("false");
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  assertThat(html.select("#designer-ai-result-content .designer-ai-card")).isEmpty();
  verifyNoInteractions(aiService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"ai", "AI", "aI"})
 void explicitAiModeDefersRecommendationsWithInvalidHiddenSearchConditions(String mode) throws Exception {
  var result = mvc.perform(get("/designers").param("mode", mode).param("query", "염색").param("limit", "3")
    .param("keyword", "커트").param("salonKeyword", "스타일").param("minRating", "bad")
    .param("minCareerYears", "bad").param("minReviewCount", "bad").param("page", "bad"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attribute("searchMode", "ai"))
   .andExpect(model().attributeHasFieldErrors("search", "minRating", "minCareerYears", "minReviewCount", "page")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertSelectedMode(html, "ai");
  assertThat(html.selectFirst("#designer-ai-validation").hasAttr("hidden")).isTrue();
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-complete")).isEqualTo("false");
  assertThat(html.selectFirst("#designer-ai-result-content").hasAttr("hidden")).isTrue();
  assertThat(html.select("#designer-ai-result-content .designer-ai-card")).isEmpty();
  assertThat(html.selectFirst("#designer-results-area").attr("data-current-page")).isEqualTo("1");
  assertThat(html.select("#designer-results-area .designer-card")).isEmpty();
  assertThat(html.selectFirst("#designer-search-panel input[name=page]").val()).isEqualTo("bad");
  verifyNoInteractions(queryService, aiService);
  if ("ai".equals(mode)) writeAiBrowserFixture("AI-invalid-search.html", rendered);
 }

 @ParameterizedTest
 @ValueSource(strings = {"limit", "query", "both"})
 void explicitSearchModeIgnoresInvalidHiddenAiConditions(String invalidField) throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15))).thenReturn(catalogPage(0));
  boolean invalidQuery = !"limit".equals(invalidField);
  boolean invalidLimit = !"query".equals(invalidField);
  String query = invalidQuery ? "염".repeat(301) : "염색";
  var result = mvc.perform(filteredSearchRequest("search", 1).param("query", query)
    .param("limit", invalidLimit ? "bad" : "3"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attribute("searchMode", "search")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertSelectedMode(html, "search");
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo(query);
  assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo(Boolean.toString(invalidQuery || invalidLimit));
  assertThat(html.select("#aiLimit")).isEmpty();
  assertThat(html.select("#designer-results-area .catalog-validation")).isEmpty();
  assertThat(html.select("#designer-results-area [data-designer-sort-panel=rating] .designer-card")).hasSize(1);
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoMoreInteractions(queryService);
  verifyNoInteractions(aiService);
  if ("both".equals(invalidField)) writeAiBrowserFixture("search-invalid-ai.html", rendered);
 }

 @Test
 void explicitSearchPageTwoExposesCurrentPageForModeRoundTrip() throws Exception {
  when(queryService.searchCatalog(any(), eq(1), eq(15))).thenReturn(catalogPage(1));
  var result = mvc.perform(filteredSearchRequest("search", 2).param("query", "염색").param("limit", "3"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list"))
   .andExpect(model().attribute("currentPage", 2)).andExpect(model().attribute("totalDesignerCount", 45L)).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertSelectedMode(html, "search");
  assertThat(html.selectFirst("#designer-results-area").attr("data-current-page")).isEqualTo("2");
  assertThat(html.selectFirst("#designer-results-area [data-designer-sort-panel=rating] .designer-card__name a").attr("href"))
   .isEqualTo("/designers/16");
  verify(queryService).searchCatalog(argThat(request -> request.getPage() == 2), eq(1), eq(15));
  verifyNoInteractions(aiService);
  writeAiBrowserFixture("search-page2.html", rendered);
 }

 @ParameterizedTest
 @CsvSource({"45,1,first", "45,2,middle", "45,3,last", "1,1,single", "0,1,empty"})
 void catalogPaginationMatchesSalonsAndPreservesEveryCondition(int total, int pageNumber, String fixture) throws Exception {
  var rows = java.util.stream.IntStream.range(0, Math.min(15, total))
   .mapToObj(index -> designer((pageNumber - 1) * 15 + index + 1)).toList();
  Page<DesignerSummaryResponse> page = new PageImpl<>(rows, PageRequest.of(pageNumber - 1, 15), total);
  when(queryService.searchCatalog(any(), eq(pageNumber - 1), eq(15)))
   .thenReturn(new DesignerCatalogPage(page, page, page));
  var result = mvc.perform(filteredSearchRequest("search", pageNumber).param("searched", "true"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  var heading = html.select("#designer-results-area #designer-results-heading.catalog-results-heading");
  assertThat(heading).hasSize(1);
  assertThat(heading.select(".catalog-results-heading__row > h2")).hasSize(1);
  assertThat(html.select(".catalog-pagination-wrap")).isEmpty();
  assertThat(html.select(".catalog-pagination")).hasSize(total > 15 ? 1 : 0);
  var pagination = heading.select(".catalog-results-heading__row > nav.catalog-pagination[aria-label='디자이너 목록 페이지']");
  if (total <= 15) {
   assertThat(pagination).isEmpty();
   assertThat(html.select("[data-designer-page-link]")).isEmpty();
  } else {
   assertThat(pagination).hasSize(1);
   assertThat(pagination.first().previousElementSibling().normalName()).isEqualTo("h2");
   assertThat(html.getAllElements().indexOf(pagination.first()))
    .isLessThan(html.getAllElements().indexOf(html.selectFirst(".designer-results-panels")));
   assertThat(pagination.select(".catalog-pagination__current[aria-current=page]").text()).isEqualTo(Integer.toString(pageNumber));
   assertThat(pagination.select(".catalog-pagination__total").text()).isEqualTo("3");
   assertThat(pagination.select(".catalog-pagination__button")).hasSize(2);
   assertThat(pagination.select(".catalog-pagination__status").attr("aria-label"))
    .isEqualTo("현재 " + pageNumber + "페이지, 전체 3페이지");
   assertThat(pagination.select("[aria-disabled=true]")).hasSize(pageNumber == 2 ? 0 : 1);
   assertThat(pagination.select("[aria-disabled=true][href], [aria-disabled=true][tabindex], .page-link, [data-service-page-link]")).isEmpty();
   var links = pagination.select("a[data-designer-page-link]");
   assertThat(links).hasSize(pageNumber == 2 ? 2 : 1);
   links.forEach(link -> {
    int target = "prev".equals(link.attr("rel")) ? pageNumber - 1 : pageNumber + 1;
    String decoded = java.net.URLDecoder.decode(link.attr("href"), StandardCharsets.UTF_8);
    assertThat(decoded).startsWith("/designers?").contains("mode=search", "page=" + target, "searched=true",
     "keyword=커트", "salonKeyword=스타일", "minRating=4", "minCareerYears=5", "minReviewCount=2");
    assertThat(link.select("svg[aria-hidden=true][focusable=false]")).hasSize(1);
   });
  }
  verify(queryService).searchCatalog(any(), eq(pageNumber - 1), eq(15));
  verifyNoInteractions(aiService);
  Path directory = Path.of("build", "catalog-pagination", "fixtures");
  Files.createDirectories(directory);
  Files.writeString(directory.resolve("designer-" + fixture + ".html"), rendered, StandardCharsets.UTF_8);
 }

 @Test
 void validPublicSearchUsesOneCatalogSnapshot() throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15)))
   .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  var result = mvc.perform(get("/designers")).andExpect(status().isOk()).andExpect(view().name("designer/list")).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select("select[name=limit], #aiLimit")).isEmpty();
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoMoreInteractions(queryService);
  verifyNoInteractions(aiService);
  writeBrowserFixture("empty.html", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
 }

 @Test
 void populatedResultsRenderSharedSortWidgetAndKeepAllThreePanels() throws Exception {
  when(queryService.searchCatalog(any(), eq(0), eq(15))).thenReturn(new DesignerCatalogPage(
   new PageImpl<>(List.of(designer(1))), new PageImpl<>(List.of(designer(2))), new PageImpl<>(List.of(designer(3)))));
  var result = mvc.perform(get("/designers").param("mode", "search"))
   .andExpect(status().isOk()).andExpect(view().name("designer/list")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  var widget = html.selectFirst(".designer-results-sort [data-catalog-select]");
  assertThat(widget).isNotNull();
  assertThat(widget.select("select[data-designer-result-sort] option").eachAttr("value"))
   .containsExactly("rating", "likes", "newest");
  assertThat(widget.selectFirst(".catalog-select__trigger").attr("aria-controls")).isEqualTo("designer-sort-options");
  assertThat(widget.selectFirst("#designer-sort-options").attr("role")).isEqualTo("listbox");
  assertThat(html.select("[data-designer-sort-panel]").eachAttr("data-designer-sort-panel"))
   .containsExactly("rating", "likes", "newest");
  assertThat(html.selectFirst("script[src='/js/catalog-select.js']")).isNotNull();
  assertThat(html.selectFirst("link[href='/css/catalog-select.css']")).isNotNull();
  verify(queryService).searchCatalog(any(), eq(0), eq(15));
  verifyNoInteractions(aiService);
  writeBrowserFixture("list.html", rendered);
 }

 private DesignerSummaryResponse designer(int id) {
  return DesignerSummaryResponse.builder().designerId(id).salonId(1).salonName("테스트 살롱")
   .name("디자이너 " + id).careerYears(5).averageRating(new BigDecimal("4.8")).reviewCount(2L).likeCount(id).build();
 }

 private MockHttpServletRequestBuilder filteredSearchRequest(String mode, int page) {
  return get("/designers").param("mode", mode).param("keyword", "커트").param("salonKeyword", "스타일")
   .param("minRating", "4").param("minCareerYears", "5").param("minReviewCount", "2").param("page", Integer.toString(page));
 }

 private DesignerCatalogPage catalogPage(int number) {
  Page<DesignerSummaryResponse> page = new PageImpl<>(List.of(designer(number * 15 + 1)), PageRequest.of(number, 15), 45);
  return new DesignerCatalogPage(page, page, page);
 }

 private void assertSelectedMode(Document html, String mode) {
  assertThat(html.select(".designer-search-mode-switch__button.is-active").eachAttr("data-designer-mode-target"))
   .containsExactly(mode, mode);
  html.select("[data-designer-mode-panel]").forEach(panel ->
   assertThat(panel.hasAttr("hidden")).as("visibility of %s", panel.id())
    .isEqualTo(!mode.equals(panel.attr("data-designer-mode-panel"))));
 }

 private DesignerAiRecommendationResponse aiResponse(String query) {
  var item = DesignerAiRecommendationItemResponse.builder().designerId(1).salonId(1)
   .designerName("김민지").salonName("스타일 살롱").careerYears(5).averageRating(new BigDecimal("4.8"))
   .reviewCount(2L).likeCount(3).reason("원하는 스타일에 맞는 디자이너입니다.").tags(List.of("염색", "상담")).build();
  return DesignerAiRecommendationResponse.builder().query(query).summary("입력한 조건에 맞는 추천입니다.")
   .source("LOCAL_RULES").llmUsed(false).recommendations(List.of(item)).build();
 }

 private void writeAiBrowserFixture(String name, String html) throws Exception {
  Path directory = Path.of("build", "designer-ai", "fixtures");
  Files.createDirectories(directory);
  Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
 }

 private void writeBrowserFixture(String name, String html) throws Exception {
  Path directory = Path.of("build", "designer-select", "fixtures");
  Files.createDirectories(directory);
  Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
 }
}

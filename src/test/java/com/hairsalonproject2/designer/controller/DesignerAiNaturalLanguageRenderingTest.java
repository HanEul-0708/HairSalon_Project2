package com.hairsalonproject2.designer.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.llm.DesignerAiClient;
import com.hairsalonproject2.designer.service.DesignerAiCandidateService;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises validation, real recommendation selection and Thymeleaf together without a database or AI network call. */
@WebMvcTest({DesignerController.class, DesignerAiController.class})
@Import({SecurityConfig.class, DesignerAiRecommendationService.class})
class DesignerAiNaturalLanguageRenderingTest {
 @Autowired private MockMvc mvc;
 @MockitoBean private DesignerQueryService queryService;
 @MockitoBean private DesignerAiCandidateService candidates;
 @MockitoBean private DesignerAiClient aiClient;
 @MockitoBean private CustomUserDetailsService userDetailsService;
 @MockitoBean private JpaMetamodelMappingContext metamodel;

 @BeforeEach
 void configureCandidates() {
  // The salon offers every service; each designer's own specialty remains decisive.
  when(candidates.loadCandidates()).thenReturn(IntStream.rangeClosed(1, 12).mapToObj(id ->
   new DesignerAiCandidatePrompt(id, 1, "디자이너 " + id, "스타일 살롱", id <= 10 ? "CUT" : "PERM",
    id, new BigDecimal("4.50"), 3L, 0, id <= 10 ? "커트 전문입니다." : "펌 전문입니다.",
    List.of("커트", "펌", "염색", "스타일링"), List.of())).toList());
  when(aiClient.recommend(anyString(), anyList(), anyInt())).thenReturn(Optional.empty());
 }

 @ParameterizedTest
 @CsvSource({
  "경력 4년 이상 커트 디자이너 추천해줘, 7",
  "경력 4년 이상 커트 디자이너 2명 추천해줘, 2",
  "경력 4년 이상 커트 디자이너 두 명 추천해줘, 2",
  "커트 디자이너 7명 추천해줘, 7",
  "커트 디자이너 100명 추천해줘, 10",
  "커트 디자이너 999999999999999999999999999999명 추천해줘, 10",
  "경력 4년 이하 커트 디자이너 추천해줘, 4",
  "최대 경력 4년인 커트 디자이너 추천해줘, 4",
  "경력 4년 미만 커트 디자이너 추천해줘, 3",
  "평점 4.5점 초과 커트 디자이너 추천해줘, 0",
  "평점 4.5점 이하 커트 디자이너 추천해줘, 10",
  "평점이 4.6 이상인 커트 디자이너 추천해줘, 0",
  "최대 평점 4.4점인 커트 디자이너 추천해줘, 0",
  "경력 20년 이상 커트 디자이너 추천해줘, 0"
 })
 void publicFragmentUsesNaturalCountAndStrictConditions(String query, int expectedCount) throws Exception {
  var result = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", query))
   .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  var html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select(".designer-ai-card")).hasSize(expectedCount);
  assertThat(html.selectFirst("#aiQuery").val()).isEqualTo(query);
  assertThat(html.select("select[name=limit], #aiLimit")).isEmpty();
  assertThat(html.selectFirst("#designer-ai-result-content").attr("data-complete")).isEqualTo("true");
  if (expectedCount == 0) {
   assertThat(html.select(".designer-empty-state")).hasSize(1);
   verifyNoInteractions(aiClient);
  } else {
   verify(aiClient).recommend(eq(query), argThat(items -> items.stream()
    .allMatch(item -> item.designerId() <= 10)), eq(Math.min(expectedCount, 8)));
  }
  verifyNoInteractions(queryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"디자이너 0명 추천해줘", "디자이너 -1명 추천해줘", "디자이너 1.5명 추천해줘", "디자이너 2명과 3명 추천해줘"})
 void invalidNaturalCountNeverLoadsCandidatesForEitherEndpoint(String query) throws Exception {
  var fragment = mvc.perform(get("/designers").header("X-Requested-With", "XMLHttpRequest")
    .param("mode", "ai").param("query", query))
   .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "query")).andReturn();
  mvc.perform(get("/designers/ai/recommendations").with(user("member")).param("query", query))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("query"));
  if (query.equals("디자이너 0명 추천해줘")) {
   var fullPage = mvc.perform(get("/designers").param("mode", "ai").param("query", query))
    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("aiRequest", "query")).andReturn();
   var html = Jsoup.parse(fullPage.getResponse().getContentAsString(StandardCharsets.UTF_8));
   assertThat(html.selectFirst("#aiQuery").attr("aria-invalid")).isEqualTo("true");
   assertThat(html.select("#designer-ai-validation [data-designer-ai-field=query]").text()).contains("1명 이상");
   Path directory = Path.of("build", "designer-ai", "fixtures");
   Files.createDirectories(directory);
   Files.writeString(directory.resolve("natural-validation.html"), fullPage.getResponse().getContentAsString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
   Files.writeString(directory.resolve("natural-invalid-fragment.html"), fragment.getResponse().getContentAsString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }
  verifyNoInteractions(candidates, aiClient, queryService);
 }

 @Test
 void jsonEndpointKeepsOptionalLegacyLimitButNaturalCountWins() throws Exception {
  mvc.perform(get("/designers/ai/recommendations").with(user("member"))
    .param("query", "커트 디자이너 2명 추천해줘").param("limit", "7"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.recommendations.length()").value(2));
  mvc.perform(get("/designers/ai/recommendations").with(user("member"))
    .param("query", "커트 디자이너 추천해줘").param("limit", "7"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.recommendations.length()").value(7));
  mvc.perform(get("/designers/ai/recommendations").with(user("member"))
    .param("query", "커트 디자이너 추천해줘"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.recommendations.length()").value(10));
 }
}

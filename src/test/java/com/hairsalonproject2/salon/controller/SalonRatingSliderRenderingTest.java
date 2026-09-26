package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SalonController.class)
@Import(SecurityConfig.class)
class SalonRatingSliderRenderingTest {
 @Autowired
 private MockMvc mockMvc;
 @MockitoBean
 private SalonQueryService salonQueryService;
 @MockitoBean
 private ExternalSalonSyncService externalSalonSyncService;
 @MockitoBean
 private KakaoLocalSearchClient kakaoLocalSearchClient;
 @MockitoBean
 private CustomUserDetailsService customUserDetailsService;
 @MockitoBean
 private JpaMetamodelMappingContext jpaMetamodelMappingContext;

 @BeforeEach
 void useOnlyMockedCatalogData() {
  when(salonQueryService.search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
 }

 @ParameterizedTest(name = "rating step {0} renders matching value and evenly positioned ticks")
 @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
 void rendersAllRatingStepsWithUniformTickPositions(int stepIndex) throws Exception {
  String rating = BigDecimal.valueOf(stepIndex).divide(BigDecimal.valueOf(2))
          .stripTrailingZeros().toPlainString();
  var request = get("/salons");
  if (stepIndex > 0) request.param("minRating", rating);

  var result = mockMvc.perform(request)
          .andExpect(status().isOk())
          .andExpect(view().name("salon/list"))
          .andReturn();

  String renderedPage = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(renderedPage);
  var slider = html.selectFirst("[data-rating-slider]");
  assertThat(slider).isNotNull();
  assertThat(slider.attr("data-rating-active")).isEqualTo(Boolean.toString(stepIndex > 0));

  var input = slider.selectFirst("input[data-rating-slider-input]");
  assertThat(input).isNotNull();
  assertThat(input.attr("type")).isEqualTo("range");
  assertThat(input.attr("min")).isEqualTo("0");
  assertThat(input.attr("max")).isEqualTo("5");
  assertThat(input.attr("step")).isEqualTo("0.5");
  assertThat(input.val()).isEqualTo(rating);
  assertThat(input.attr("aria-label")).isEqualTo("최소 평점");
  assertThat(input.hasAttr("name")).isFalse();
  assertThat(slider.select("input[type=hidden][name=minRating]")).hasSize(1);
  assertThat(slider.selectFirst("input[name=minRating]").val())
          .isEqualTo(stepIndex == 0 ? "" : rating);
  assertThat(slider.selectFirst("[data-rating-slider-value]").text())
          .isEqualTo(stepIndex == 0 ? "전체" : rating + "점");

  var track = slider.selectFirst(".salon-rating-slider__track");
  assertThat(track).isNotNull();
  assertThat(track.attr("aria-hidden")).isEqualTo("true");
  assertThat(track.children()).hasSize(1);
  assertThat(track.child(0).hasClass("salon-rating-slider__fill")).isTrue();
  assertThat(slider.selectFirst(".salon-rating-slider__ticks").attr("aria-hidden"))
          .isEqualTo("true");

  var ticks = slider.select(".salon-rating-slider__tick");
  assertThat(ticks).hasSize(11);
  assertThat(slider.select(".salon-rating-slider__tick--major")).hasSize(6);
  assertThat(slider.select(".salon-rating-slider__tick--minor")).hasSize(5);
  for (int index = 0; index < ticks.size(); index++) {
   var tick = ticks.get(index);
   assertThat(new BigDecimal(tick.attr("data-rating-value")))
           .isEqualByComparingTo(BigDecimal.valueOf(index).divide(BigDecimal.valueOf(2)));
   assertThat(tick.attr("style").replaceAll("\\s+", ""))
           .contains("--rating-tick-position:" + index * 10 + "%;");
   assertThat(tick.hasClass("salon-rating-slider__tick--major")).isEqualTo(index % 2 == 0);
   assertThat(tick.hasClass("salon-rating-slider__tick--minor")).isEqualTo(index % 2 != 0);
  }

  // The browser regression uses the real rendered page without starting the application or a database.
  Path fixtureDirectory = Path.of("build", "rating-slider", "fixtures");
  Files.createDirectories(fixtureDirectory);
  Files.writeString(fixtureDirectory.resolve(stepIndex + ".html"), renderedPage, StandardCharsets.UTF_8);
 }
}

package com.hairsalonproject2.common.catalog;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.designer.controller.DesignerController;
import com.hairsalonproject2.designer.dto.response.DesignerCatalogPage;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salonservice.controller.ServiceController;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({DesignerController.class, ServiceController.class})
@Import(SecurityConfig.class)
class CatalogRangeRenderingTest {
 @Autowired private MockMvc mvc;
 @MockitoBean private DesignerQueryService designers;
 @MockitoBean private DesignerAiRecommendationService recommendations;
 @MockitoBean private SalonServiceQueryService services;
 @MockitoBean private CustomUserDetailsService userDetailsService;
 @MockitoBean private JpaMetamodelMappingContext metamodel;

 @ParameterizedTest
 @ValueSource(strings = {"designer", "service"})
 void unsetFiltersRenderAllLabelsAndOneEmptySubmissionValue(String catalog) throws Exception {
  mockEmptyCatalog(catalog);

  var result = mvc.perform(get(pathFor(catalog)))
          .andExpect(status().isOk()).andExpect(view().name(catalog + "/list"))
          .andExpect(model().hasNoErrors()).andReturn();
  Document html = rendered(result, catalog + "-empty.html");
  if ("service".equals(catalog)) {
   assertThat(html.select("#service-results-area[aria-live=polite][aria-busy=false]")).hasSize(1);
   assertThat(html.select("#service-search-message[role=alert][hidden]")).hasSize(1);
   assertThat(html.select("#service-search-form [data-service-page-error]")).hasSize(1);
   assertThat(html.select("#service-search-form button[type=submit]")).isEmpty();
   var toolbar = html.selectFirst(".service-results-head.catalog-results-head");
   assertThat(toolbar).isNotNull();
   assertThat(toolbar.closest("#service-results-area, form")).isNull();
   assertThat(toolbar.hasAttr("hidden")).isFalse();
   assertThat(toolbar.parents()).noneMatch(parent -> parent.hasAttr("hidden"));
   assertThat(toolbar.selectFirst("#service-results-heading").text()).contains("0");
   var sort = toolbar.selectFirst("select[name=sortBy][form=service-search-form]");
   assertThat(sort).isNotNull();
   assertThat(sort.select("option[selected]").eachAttr("value")).containsExactly("");
   assertThat(html.select("#service-search-form select[name=sortBy]")).isEmpty();
  } else {
   var groups = html.select("#designer-search-panel fieldset.designer-search-form__ranges");
   assertThat(groups).hasSize(1);
   var group = groups.first();
   assertThat(group.select("[data-catalog-range]").eachAttr("data-catalog-range"))
    .containsExactly("minRating", "minCareerYears", "minReviewCount");
   assertThat(group.select("[data-catalog-range]").eachAttr("data-step"))
    .containsExactly("0.5", "1", "1");
   assertThat(group.select("legend.visually-hidden")).hasSize(1);
   assertThat(group.selectFirst("legend").text()).isNotBlank();
   assertThat(group.closest(".designer-form-layout__inputs")).isNotNull();
   assertThat(html.select("#designer-search-panel [data-catalog-range]").stream()
    .allMatch(slider -> slider.closest("fieldset") == group)).isTrue();
  }
  List<String> fields = fieldsFor(catalog);
  assertThat(html.select("[data-catalog-range]")).hasSize(fields.size());
  for (String field : fields) {
   Element slider = slider(html, field);
   assertSharedGeometry(slider);
   assertThat(slider.selectFirst("input[type=hidden][name=" + field + "]").val()).isEmpty();
   assertThat(slider.selectFirst("[data-catalog-range-output]").text()).isEqualTo("전체");
   Element input = slider.selectFirst("[data-catalog-range-input]");
   assertThat(new BigDecimal(input.val())).isEqualByComparingTo(input.attr("min"));
   assertThat(slider.attr("data-invalid")).isEqualTo("false");
  }
  verifyNoInteractions(recommendations);
 }

 @ParameterizedTest
 @CsvSource({
         "selected, 4.5, 5, 20",
         "off-step, 4.1, 13, 37",
         "precision, 4.25, 13, 37",
         "outside, 5, 45, 1501",
         "large, 5, 2147483647, 9223372036854775807"
 })
 void designerFiltersPreserveExactValidValuesAndQueryParameters(String fixture, String rating,
                                                               String career, String reviews) throws Exception {
  mockEmptyCatalog("designer");

  var result = mvc.perform(get("/designers").param("minRating", rating)
                  .param("minCareerYears", career).param("minReviewCount", reviews))
          .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  Document html = rendered(result, "designer-" + fixture + ".html");
  assertSelected(html, "minRating", rating, "점");
  assertSelected(html, "minCareerYears", career, "년");
  assertSelected(html, "minReviewCount", reviews, "건");
  verify(designers).searchCatalog(argThat(request ->
          request.getMinRating().compareTo(new BigDecimal(rating)) == 0
                  && request.getMinCareerYears().equals(Integer.valueOf(career))
                  && request.getMinReviewCount().equals(Long.valueOf(reviews))), eq(0), eq(15));
  verifyNoMoreInteractions(designers);
  verifyNoInteractions(services, recommendations);
 }

 @ParameterizedTest
 @CsvSource({
         "selected, 120000, 120",
         "off-step, 12345, 61",
         "outside, 350001, 361",
         "zero, 0, 1",
         "large, 2147483647, 2147483647"
 })
 void serviceFiltersPreserveExactValidValuesIncludingZeroPriceAndOneMinute(String fixture, String price,
                                                                         String duration) throws Exception {
  mockEmptyCatalog("service");

  var result = mvc.perform(get("/salon-services").param("maxPrice", price).param("maxDuration", duration))
          .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  Document html = rendered(result, "service-" + fixture + ".html");
  assertSelected(html, "maxPrice", price, "원");
  assertSelected(html, "maxDuration", duration, "분");
  verify(services).list(argThat(request -> request.getMaxPrice().equals(Integer.valueOf(price))
          && request.getMaxDuration().equals(Integer.valueOf(duration))), eq(0), eq(15));
  verifyNoMoreInteractions(services);
  verifyNoInteractions(designers, recommendations);
 }

 @Test
 void explicitZeroMinimumsRemainUnchangedInSubmittedValues() throws Exception {
  mockEmptyCatalog("designer");

  var result = mvc.perform(get("/designers").param("minRating", "0")
                  .param("minCareerYears", "0").param("minReviewCount", "0"))
          .andExpect(status().isOk()).andExpect(model().hasNoErrors()).andReturn();
  Document html = rendered(result, "designer-zero.html");
  for (String field : fieldsFor("designer")) {
   assertThat(slider(html, field).selectFirst("input[type=hidden][name=" + field + "]").val())
           .isEqualTo("0");
  }
  verify(designers).searchCatalog(argThat(request -> request.getMinRating().signum() == 0
          && request.getMinCareerYears() == 0 && request.getMinReviewCount() == 0), eq(0), eq(15));
  verifyNoInteractions(recommendations);
 }

 @ParameterizedTest
 @CsvSource({
         "designer, minRating, 문자, typeMismatch",
         "designer, minRating, -0.1, DecimalMin",
         "designer, minRating, 5.1, DecimalMax",
         "designer, minCareerYears, 2147483648, typeMismatch",
         "designer, minCareerYears, -1, Min",
         "designer, minReviewCount, 9223372036854775808, typeMismatch",
         "designer, minReviewCount, -1, Min",
         "service, maxPrice, 문자, typeMismatch",
         "service, maxPrice, 2147483648, typeMismatch",
         "service, maxPrice, -1, Min",
         "service, maxDuration, 문자, typeMismatch",
         "service, maxDuration, 2147483648, typeMismatch",
         "service, maxDuration, 0, Min"
 })
 void invalidFiltersPreserveRejectedTextAndFieldErrorsWithoutQueries(String catalog, String field,
                                                                    String rejected, String code) throws Exception {
  var result = mvc.perform(get(pathFor(catalog)).param(field, rejected).param("keyword", "커트"))
          .andExpect(status().isOk()).andExpect(view().name(catalog + "/list"))
          .andExpect(model().attributeHasFieldErrorCode("search", field, code)).andReturn();
  Document html = rendered(result, catalog + "-invalid-" + field + "-" + code + ".html");
  Element slider = slider(html, field);
  assertSharedGeometry(slider);
  assertThat(slider.attr("data-invalid")).isEqualTo("true");
  assertThat(slider.selectFirst("input[type=hidden][name=" + field + "]").val()).isEqualTo(rejected);
  assertThat(slider.selectFirst("[data-catalog-range-input]").attr("aria-invalid")).isEqualTo("true");
  assertThat(slider.selectFirst("[data-catalog-range-output]").text()).isEqualTo("입력 오류");
  assertThat(slider.select(".catalog-range__error").text()).contains(rejected)
          .doesNotContain("Failed to convert", "java.lang");
  assertThat(html.selectFirst(".designer-search-form, .service-search-form")
          .selectFirst("input[name=keyword]").val()).isEqualTo("커트");
  verifyNoInteractions(designers, services, recommendations);
 }

 @ParameterizedTest
 @ValueSource(strings = {"designer", "service"})
 void allInvalidFilterValuesStayInTheirOwnControls(String catalog) throws Exception {
  var request = get(pathFor(catalog));
  for (String field : fieldsFor(catalog)) request.param(field, "invalid-" + field);

  var result = mvc.perform(request).andExpect(status().isOk())
          .andExpect(model().attributeHasFieldErrors("search", fieldsFor(catalog).toArray(String[]::new)))
          .andReturn();
  Document html = rendered(result, catalog + "-invalid.html");
  for (String field : fieldsFor(catalog)) {
   Element slider = slider(html, field);
   assertThat(slider.selectFirst("input[type=hidden][name=" + field + "]").val())
           .isEqualTo("invalid-" + field);
   assertThat(slider.select(".catalog-range__error").text()).contains("invalid-" + field);
  }
  verifyNoInteractions(designers, services, recommendations);
 }

 private void mockEmptyCatalog(String catalog) {
  if ("designer".equals(catalog)) {
   when(designers.searchCatalog(any(), eq(0), eq(15)))
           .thenReturn(new DesignerCatalogPage(Page.empty(), Page.empty(), Page.empty()));
  } else {
   when(services.list(any(), eq(0), eq(15))).thenReturn(Page.empty());
  }
 }

 private Element slider(Document html, String field) {
  assertThat(html.select("input[name=" + field + "]")).hasSize(1);
  Element slider = html.selectFirst("[data-catalog-range=" + field + "]");
  assertThat(slider).as("slider for %s", field).isNotNull();
  if (field.startsWith("max")) {
   Element form = slider.closest("form");
   assertThat(form).isNotNull();
   assertThat(form.id()).isEqualTo("service-search-form");
   assertThat(form.attr("data-service-live-search")).isEqualTo("true");
   assertThat(form.select("[data-service-page-error]")).hasSize(1);
  }
  return slider;
 }

 private void assertSelected(Document html, String field, String value, String unit) {
  Element slider = slider(html, field);
  assertSharedGeometry(slider);
  assertThat(slider.attr("data-invalid")).isEqualTo("false");
  assertThat(slider.selectFirst("input[type=hidden][name=" + field + "]").val()).isEqualTo(value);
  Element input = slider.selectFirst("[data-catalog-range-input]");
  assertThat(new BigDecimal(input.val())).isEqualByComparingTo(value);
  assertThat(new BigDecimal(input.attr("max"))).isGreaterThanOrEqualTo(new BigDecimal(value));
  BigDecimal step = new BigDecimal(slider.attr("data-step"));
  assertThat(step).isPositive();
  if (new BigDecimal(value).subtract(new BigDecimal(input.attr("min"))).remainder(step).signum() != 0) {
   assertThat(input.attr("step")).isEqualTo("any");
  }
  String label = slider.selectFirst("[data-catalog-range-output]").text();
  assertThat(label).endsWith(unit);
  assertThat(new BigDecimal(label.substring(0, label.length() - unit.length()).replace(",", "").trim()))
          .isEqualByComparingTo(value);
 }

 private void assertSharedGeometry(Element slider) {
  Element visualSlider = slider.selectFirst(".salon-rating-slider");
  assertThat(visualSlider).isNotNull();
  Element input = visualSlider.selectFirst("[data-catalog-range-input]");
  assertThat(input).isNotNull();
  assertThat(input.attr("type")).isEqualTo("range");
  assertThat(input.hasAttr("name")).isFalse();
  assertThat(input.id()).isNotBlank();
  Element label = visualSlider.selectFirst("label[for=" + input.id() + "]");
  assertThat(label).isNotNull();
  assertThat(label.text()).isNotBlank();
  assertThat(input.hasClass("salon-rating-slider__input")).isTrue();
  assertThat(slider.select("input[type=hidden][data-catalog-range-value]")).hasSize(1);
  Element track = visualSlider.selectFirst(".salon-rating-slider__track");
  assertThat(track).isNotNull();
  assertThat(track.attr("aria-hidden")).isEqualTo("true");
  assertThat(track.children()).hasSize(1);
  assertThat(track.child(0).hasClass("salon-rating-slider__fill")).isTrue();
  assertThat(slider.selectFirst(".salon-rating-slider__ticks").attr("aria-hidden")).isEqualTo("true");
  var ticks = slider.select(".salon-rating-slider__tick");
  assertThat(ticks).hasSize(11);
  assertThat(slider.select(".salon-rating-slider__tick--major")).hasSize(6);
  assertThat(slider.select(".salon-rating-slider__tick--minor")).hasSize(5);
  for (int index = 0; index < ticks.size(); index++) {
   assertThat(ticks.get(index).attr("style").replaceAll("\\s+", ""))
           .contains("--rating-tick-position:" + index * 10 + "%;");
  }
 }

 private Document rendered(MvcResult result, String fixture) throws Exception {
  String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  Path directory = Path.of("build", "catalog-range", "fixtures");
  Files.createDirectories(directory);
  Files.writeString(directory.resolve(fixture), html, StandardCharsets.UTF_8);
  return Jsoup.parse(html);
 }

 private List<String> fieldsFor(String catalog) {
  return "designer".equals(catalog) ? List.of("minRating", "minCareerYears", "minReviewCount")
          : List.of("maxPrice", "maxDuration");
 }

 private String pathFor(String catalog) {
  return "designer".equals(catalog) ? "/designers" : "/salon-services";
 }
}

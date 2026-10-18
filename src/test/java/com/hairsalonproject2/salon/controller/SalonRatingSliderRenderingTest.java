package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
  assertThat(html.select("form#salon-search-form.salon-search-form--primary")).hasSize(1);
  var form = html.selectFirst("#salon-search-form");
  assertThat(html.select(".salon-results-head.catalog-results-head")).hasSize(1);
  var toolbar = html.selectFirst(".salon-results-head.catalog-results-head");
  assertThat(toolbar.closest("form")).isNull();
  assertThat(toolbar.closest("#salon-results-area")).isNull();
  assertThat(toolbar.select("#salon-results-heading")).hasSize(1);
  assertThat(toolbar.select("#salon-results-area")).isEmpty();
  assertThat(html.getAllElements().indexOf(toolbar))
          .isLessThan(html.getAllElements().indexOf(html.selectFirst("#salon-results-area")));
  assertThat(toolbar.hasAttr("hidden")).isFalse();
  assertThat(toolbar.parents()).noneMatch(parent -> parent.hasAttr("hidden"));
  assertThat(html.select("select[name=sort]")).hasSize(1);
  var sort = toolbar.selectFirst("[data-salon-sort][data-catalog-select] select[name=sort]");
  assertThat(sort).isNotNull();
  assertThat(sort.attr("form")).isEqualTo(form.id());
  assertThat(sort.closest("form")).isNull();
  assertThat(form.select("select[name=sort]")).isEmpty();
  assertThat(sort.select("option").eachAttr("value")).containsExactly("recommended", "rating", "likes");
  assertThat(sort.select("option[selected]").eachAttr("value")).containsExactly("recommended");
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

 @ParameterizedTest
 @CsvSource({"0,1,empty", "15,1,single", "45,1,first", "45,2,middle", "45,3,last"})
 void paginationStaysWithResultHeadingAndPreservesFilters(int total, int page, String fixture) throws Exception {
  List<SalonSummaryResponse> salons = java.util.stream.IntStream.range(0, Math.min(total, 15))
          .mapToObj(index -> SalonSummaryResponse.builder().salonId((page - 1) * 15 + index + 1)
                  .name("성수 헤어 " + ((page - 1) * 15 + index + 1)).address("서울 성동구 성수동")
                  .averageRating(new BigDecimal("4.8")).reviewCount(3).likeCount(2).reservable(true).build())
          .toList();
  when(salonQueryService.search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
          .thenReturn(new PageImpl<>(salons, PageRequest.of(page - 1, 15), total));
  var result = mockMvc.perform(get("/salons").param("page", Integer.toString(page))
                  .param("keyword", "커트").param("region", "서울").param("minRating", "4.5")
                  .param("reservable", "true").param("sort", "rating").param("preset", "top-rated-4-5"))
          .andExpect(status().isOk()).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.selectFirst("#salon-results-heading h2").text()).isEqualTo("살롱 " + total + "개");
  assertThat(html.select("#salon-results-area [data-salon-page-link]")).isEmpty();
  var pagination = html.select("#salon-results-heading .salon-results-heading__row nav[aria-label='살롱 목록 페이지']");
  if (total <= 15) {
   assertThat(pagination).isEmpty();
   assertThat(html.select("[data-salon-page-link]")).isEmpty();
  } else {
   assertThat(pagination).hasSize(1);
   assertThat(pagination.select(".salon-pagination__current").text()).isEqualTo(Integer.toString(page));
   assertThat(pagination.select(".salon-pagination__total").text()).isEqualTo("3");
   var links = pagination.select("a[data-salon-page-link]");
   assertThat(links).hasSize(page == 2 ? 2 : 1);
   for (var link : links) {
    String href = java.net.URLDecoder.decode(link.attr("href"), StandardCharsets.UTF_8);
    assertThat(href).contains("keyword=커트", "region=서울", "minRating=4.5", "reservable=true", "sort=rating", "preset=top-rated-4-5");
    assertThat(href).contains("page=" + ("prev".equals(link.attr("rel")) ? page - 1 : page + 1));
   }
   assertThat(pagination.select("[aria-disabled=true]")).hasSize(page == 2 ? 0 : 1);
  }
  Path fixtureDirectory = Path.of("build", "salon-pagination", "fixtures");
  Files.createDirectories(fixtureDirectory);
  Files.writeString(fixtureDirectory.resolve(fixture + ".html"), rendered, StandardCharsets.UTF_8);
 }

 @Test
 void internalAndExternalCardsRenderIndependentStatusBadgesAndBrowserFixture() throws Exception {
  var salons = List.of(
          SalonSummaryResponse.builder().salonId(1).name("성수 프리미엄 헤어디자인 커트 전문 미용실")
                  .address("서울 성동구 성수동").averageRating(new BigDecimal("4.8"))
                  .reviewCount(3).likeCount(2).reservable(true).build(),
          SalonSummaryResponse.builder().salonId(2).name("LongUnbrokenInternalSalonNameForResponsiveCardVerification")
                  .address("서울 성동구 성수동").averageRating(new BigDecimal("4.5"))
                  .reviewCount(2).likeCount(1).reservable(false).build());
  when(salonQueryService.search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
          .thenReturn(new PageImpl<>(salons, PageRequest.of(0, 15), 45));
  when(kakaoLocalSearchClient.isConfigured()).thenReturn(true);
  when(kakaoLocalSearchClient.searchSalons("커트", null, 1, 15)).thenReturn(List.of(
          KakaoPlaceSearchResult.builder().externalId("external-1")
                  .placeName("LongUnbrokenExternalSalonNameForResponsiveCardVerification")
                  .addressName("서울 성동구 성수동").roadAddressName("서울 성동구 성수로 1")
                  .phone("02-123-4567").placeUrl("https://example.test/salons/external-1")
                  .averageRating(new BigDecimal("4.7")).reviewCount(4).build()));

  var result = mockMvc.perform(get("/salons").param("keyword", "커트"))
          .andExpect(status().isOk()).andExpect(view().name("salon/list")).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);
  assertThat(html.select("#salon-results-area .salon-card")).hasSize(2);
  assertThat(html.select(".salon-grid--external .salon-card")).hasSize(1);
  assertThat(html.select(".salon-card__title-row > .salon-status-badge").eachText())
          .containsExactlyInAnyOrder("예약 가능", "예약 준비중", "외부 결과");
  assertThat(html.select(".salon-card__title-row > .salon-card__name")).hasSize(3);

  Path fixtureDirectory = Path.of("build", "catalog-pagination", "fixtures");
  Files.createDirectories(fixtureDirectory);
  Files.writeString(fixtureDirectory.resolve("salon-cards.html"), rendered, StandardCharsets.UTF_8);
 }
}

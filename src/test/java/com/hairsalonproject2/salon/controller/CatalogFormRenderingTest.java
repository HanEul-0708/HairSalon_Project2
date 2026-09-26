package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.admin.controller.AdminDesignerController;
import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.controller.ServiceController;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceDetailResponse;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.dto.response.ServicePriceCompareResponse;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SalonController.class, ServiceController.class, AdminDesignerController.class})
@Import(SecurityConfig.class)
class CatalogFormRenderingTest {
 @Autowired
 private MockMvc mockMvc;
 @MockitoBean
 private SalonQueryService salonQueryService;
 @MockitoBean
 private SalonServiceQueryService salonServiceQueryService;
 @MockitoBean
 private DesignerQueryService designerQueryService;
 @MockitoBean
 private ExternalSalonSyncService externalSalonSyncService;
 @MockitoBean
 private KakaoLocalSearchClient kakaoLocalSearchClient;
 @MockitoBean
 private CustomUserDetailsService customUserDetailsService;
 @MockitoBean
 private JpaMetamodelMappingContext jpaMetamodelMappingContext;

 @Test
 void serviceListRendersFractionalRatingsAndNullAsZero() throws Exception {
  var services = List.of(service(1, new BigDecimal("4.9")), service(2, new BigDecimal("4.1")), service(3, null));
  when(salonServiceQueryService.list(any(), anyInt(), anyInt()))
          .thenReturn(new PageImpl<>(services, PageRequest.of(0, 15), services.size()));

  var result = mockMvc.perform(get("/salon-services").param("sortBy", "rating"))
          .andExpect(status().isOk())
          .andExpect(view().name("service/list"))
          .andReturn();

  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select(".service-card__meta").eachText().stream().filter(text -> text.startsWith("평점 ")))
          .containsExactly("평점 4.90", "평점 4.10", "평점 0.00");
 }

 @ParameterizedTest
 @ValueSource(strings = {"", "duration", "rating", "name", "trend"})
 void serviceSortRendersServerSelectionAndBrowserFixture(String sortBy) throws Exception {
  when(salonServiceQueryService.list(any(), anyInt(), anyInt()))
          .thenReturn(new PageImpl<>(List.of(service(1, new BigDecimal("4.9")))));
  var request = get("/salon-services");
  if (!sortBy.isEmpty()) request.param("sortBy", sortBy);
  var result = mockMvc.perform(request).andExpect(status().isOk())
          .andExpect(view().name("service/list")).andReturn();
  String renderedPage = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  Document html = Jsoup.parse(renderedPage);
  var select = html.selectFirst(".catalog-select select[name=sortBy]");
  assertThat(select).isNotNull();
  assertThat(select.select("option").eachAttr("value"))
          .containsExactly("", "duration", "rating", "name", "trend");
  assertThat(select.select("option[selected]")).hasSize(1);
  assertThat(select.selectFirst("option[selected]").val()).isEqualTo(sortBy);
  assertThat(html.selectFirst(".catalog-select__trigger").attr("type")).isEqualTo("button");
  assertThat(html.selectFirst(".service-search-form").attr("method")).isEqualTo("get");

  Path fixtureDirectory = Path.of("build", "catalog-select", "fixtures");
  Files.createDirectories(fixtureDirectory);
  Files.writeString(fixtureDirectory.resolve((sortBy.isEmpty() ? "default" : sortBy) + ".html"),
          renderedPage, StandardCharsets.UTF_8);
 }

 @Test
 void serviceComparisonRendersFractionalRatingsAndNullAsZero() throws Exception {
  when(salonServiceQueryService.compare(null, null, 0, 15)).thenReturn(new PageImpl<>(List.of(
          comparison(1, new BigDecimal("4.9")), comparison(2, new BigDecimal("4.1")), comparison(3, null))));

  var result = mockMvc.perform(get("/salon-services/compare"))
          .andExpect(status().isOk())
          .andExpect(view().name("service/compare"))
          .andReturn();

  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select(".service-compare-table tbody tr td:last-child").eachText())
          .containsExactly("4.90", "4.10", "0.00");
 }

 @ParameterizedTest
 @CsvSource({
         "/salon-services, maxPrice, 숫자아님, 최대 가격은 정수로 입력해 주세요.",
         "/salon-services, maxDuration, 2147483648, 최대 소요 시간은 정수로 입력해 주세요.",
         "/salon-services, page, 숫자아님, 페이지는 1 이상의 정수로 입력해 주세요.",
         "/salon-services, page, -1, 페이지는 1 이상으로 입력해 주세요.",
         "/salon-services/compare, page, 숫자아님, 페이지는 1 이상의 정수로 입력해 주세요.",
         "/salon-services/compare, page, 2147483648, 페이지는 1 이상의 정수로 입력해 주세요.",
         "/salon-services/compare, page, 0, 페이지는 1 이상으로 입력해 주세요."
 })
 void invalidServiceSearchRendersKoreanErrorsAndRejectedText(String path, String field, String value, String message) throws Exception {
  var result = mockMvc.perform(get(path).param(field, value).param("region", "서울 강남구"))
          .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("search", field)).andReturn();
  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var form = html.selectFirst(".service-search-form");
  assertThat(form.selectFirst("input[name=" + field + "]").val()).isEqualTo(value);
  assertThat(form.selectFirst("input[name=region]").val()).isEqualTo("서울 강남구");
  assertThat(form.select(".invalid-feedback").text()).contains(message).doesNotContain("Failed to convert", "java.lang");
  verifyNoInteractions(salonServiceQueryService);
 }

 @Test
 void servicePaginationKeepsEverySearchFilter() throws Exception {
  when(salonServiceQueryService.list(any(), anyInt(), anyInt()))
          .thenReturn(new PageImpl<>(List.of(service(16, new BigDecimal("4.9"))), PageRequest.of(1, 15), 31));
  var result = mockMvc.perform(get("/salon-services").param("keyword", "커트").param("salonKeyword", "살롱")
                  .param("region", "서울").param("maxPrice", "30000").param("maxDuration", "60")
                  .param("sortBy", "rating").param("page", "2"))
          .andExpect(status().isOk()).andReturn();
  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var href = html.select(".pagination a").getLast().attr("href");
  String decoded = java.net.URLDecoder.decode(href, StandardCharsets.UTF_8);
  assertThat(decoded).contains("page=3", "keyword=커트", "salonKeyword=살롱", "region=서울", "maxPrice=30000", "maxDuration=60", "sortBy=rating");
  assertThat(html.select(".pagination [aria-current=page]").text()).isEqualTo("2");
  assertThat(html.select("input[name=page]")).isEmpty();
 }

 @Test
 void comparisonPrefersRoadAddressFallsBackToAddressAndKeepsPageFilters() throws Exception {
  var rows = List.of(
          ServicePriceCompareResponse.builder().serviceId(1).salonId(1).salonName("도로 살롱").serviceName("커트")
                  .address("지번 주소").roadAddress("서울 도로명 주소").price(15000).duration(30).build(),
          ServicePriceCompareResponse.builder().serviceId(2).salonId(2).salonName("지번 살롱").serviceName("커트")
                  .address("서울 지번 주소").roadAddress(" ").price(20000).duration(40).build());
  when(salonServiceQueryService.compare("커트", "서울", 0, 15))
          .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 15), 16));
  var result = mockMvc.perform(get("/salon-services/compare").param("serviceName", "커트").param("region", "서울"))
          .andExpect(status().isOk()).andReturn();
  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  assertThat(html.select(".service-compare-table tbody tr td:nth-child(2)").eachText())
          .containsExactly("서울 도로명 주소", "서울 지번 주소");
  String next = java.net.URLDecoder.decode(html.select(".pagination a").getLast().attr("href"), StandardCharsets.UTF_8);
  assertThat(next).contains("page=2", "serviceName=커트", "region=서울");
 }

 @Test
 void renderedServiceDeleteFormSubmitsPostWithCsrfAndWithoutMethodOverride() throws Exception {
  when(salonServiceQueryService.getDetail(7)).thenReturn(SalonServiceDetailResponse.builder()
          .serviceId(7).salonId(1).salonName("살롱").name("커트").price(15000).duration(30).build());
  var result = mockMvc.perform(get("/salon-services/7").with(user("admin").roles("ADMIN")))
          .andExpect(status().isOk()).andReturn();
  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var deleteForm = html.selectFirst("form[data-service-delete]");
  assertThat(deleteForm.attr("method")).isEqualToIgnoringCase("post");
  assertThat(deleteForm.attr("action")).isEqualTo("/salon-services/7/delete");
  assertThat(deleteForm.select("input[name=_method]")).isEmpty();
  assertThat(deleteForm.select("input[name=_csrf]")).isNotEmpty();
  mockMvc.perform(post(deleteForm.attr("action")).with(user("admin").roles("ADMIN")).with(csrf()))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salon-services"));
  verify(salonServiceQueryService).delete(7);
 }

 @ParameterizedTest
 @CsvSource({
         "/salons, salon/form, reservable, 예약 가능 여부를 선택해 주세요.",
         "/salon-services, service/form, price, 가격은 정수로 입력해 주세요.",
         "/admin/designers, designer/form, careerYears, 경력은 정수로 입력해 주세요.",
         "/salons/7/edit, salon/form, reservable, 예약 가능 여부를 선택해 주세요.",
         "/salon-services/7/edit, service/form, price, 가격은 정수로 입력해 주세요.",
         "/admin/designers/7/edit, designer/form, careerYears, 경력은 정수로 입력해 주세요."
 })
 void invalidSubmissionRendersFieldErrorsAndKeepsFormValues(String path, String viewName, String invalidField, String message) throws Exception {
  var result = mockMvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf())
                  .param("name", "입력한 이름 유지")
                  .param("address", " ")
                  .param("salonId", "")
                  .param("price", "숫자아님")
                  .param("duration", "0")
                  .param("careerYears", "숫자아님")
                  .param("reservable", "invalid"))
          .andExpect(status().isOk())
          .andExpect(view().name(viewName))
          .andExpect(model().attributeHasFieldErrors("form", invalidField))
          .andReturn();

  Document html = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  var form = html.selectFirst("main form");
  assertThat(form).isNotNull();
  assertThat(form.attr("action")).isEqualTo(path);
  assertThat(form.selectFirst("input[name=name]").val()).isEqualTo("입력한 이름 유지");
  assertThat(form.select(".invalid-feedback").text()).contains(message);
  assertThat(form.text()).doesNotContain("Failed to convert", "java.lang");
  assertThat(form.select("input[name=_csrf]")).isNotEmpty();
  if (!invalidField.equals("reservable")) {
   assertThat(form.selectFirst("input[name=" + invalidField + "]").attr("value")).isEqualTo("숫자아님");
  }
  verifyNoInteractions(salonQueryService, salonServiceQueryService, designerQueryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salon-services", "/admin/designers"})
 void memberCannotSubmitAdminCatalogForms(String path) throws Exception {
  mockMvc.perform(post(path).with(user("member").roles("USER")).with(csrf()))
          .andExpect(status().isForbidden());
  verifyNoInteractions(salonQueryService, salonServiceQueryService, designerQueryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salon-services", "/admin/designers"})
 void adminSubmissionStillRequiresCsrf(String path) throws Exception {
  mockMvc.perform(post(path).with(user("admin").roles("ADMIN")))
          .andExpect(status().isForbidden());
  verifyNoInteractions(salonQueryService, salonServiceQueryService, designerQueryService);
 }

 private SalonServiceSummaryResponse service(int id, BigDecimal rating) {
  return SalonServiceSummaryResponse.builder()
          .serviceId(id).salonId(id).salonName("테스트 살롱").address("서울")
          .name("커트").price(15000).duration(30).averageRating(rating).build();
 }

 private ServicePriceCompareResponse comparison(int id, BigDecimal rating) {
  return ServicePriceCompareResponse.builder()
          .serviceId(id).salonId(id).salonName("테스트 살롱").address("서울")
          .serviceName("커트").price(15000).duration(30).averageRating(rating).build();
 }
}

package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonBranchMarkerResponse;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SalonControllerValidationResilienceTest {

 private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
 private static final String TEMPORARY_FAILURE = "외부 검색을 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.";

 @Mock private SalonQueryService salonQueryService;
 @Mock private ExternalSalonSyncService externalSalonSyncService;
 @Mock private KakaoLocalSearchClient kakaoLocalSearchClient;
 @InjectMocks private SalonController controller;
 private MockMvc mockMvc;
 private LocalValidatorFactoryBean validator;

 @BeforeEach
 void setUp() {
  validator = new LocalValidatorFactoryBean();
  validator.afterPropertiesSet();
  InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
  viewResolver.setPrefix("/templates/");
  viewResolver.setSuffix(".html");
  mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).setViewResolvers(viewResolver).build();
 }

 @AfterEach
 void tearDown() {
  validator.close();
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salons/7/edit"})
 void missingRequiredFieldsReturnFormWithoutSaving(String url) throws Exception {
  mockMvc.perform(post(url).principal(ADMIN))
    .andExpect(status().isOk()).andExpect(view().name("salon/form"))
    .andExpect(model().attributeHasFieldErrors("form", "name", "address"));
  verifyNoInteractions(salonQueryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salons/7/edit"})
 void blankRequiredFieldsPreserveInputAndEditId(String url) throws Exception {
  MvcResult result = mockMvc.perform(post(url).principal(ADMIN)
      .param("name", "   ").param("address", " ").param("description", "입력한 소개"))
    .andExpect(status().isOk()).andExpect(view().name("salon/form"))
    .andExpect(model().attributeHasFieldErrors("form", "name", "address")).andReturn();
  BindingResult binding = bindingResult(result);
  assertThat(binding.getFieldValue("name")).isEqualTo("   ");
  assertThat(binding.getFieldValue("description")).isEqualTo("입력한 소개");
  assertThat(result.getModelAndView().getModel().get("salonId")).isEqualTo(url.endsWith("/edit") ? 7 : null);
  verifyNoInteractions(salonQueryService);
 }

 static Stream<Arguments> oversizedFields() {
  return Stream.of("/salons", "/salons/7/edit").flatMap(url -> {
   Stream<Arguments> common = Stream.of(
     Arguments.of(url, "name", 100), Arguments.of(url, "address", 255),
     Arguments.of(url, "roadAddress", 255), Arguments.of(url, "phone", 20),
     Arguments.of(url, "imageUrl", 255), Arguments.of(url, "placeUrl", 255));
   return url.equals("/salons") ? Stream.concat(common, Stream.of(
     Arguments.of(url, "externalId", 100), Arguments.of(url, "sourceType", 30))) : common;
  });
 }

 @ParameterizedTest
 @MethodSource("oversizedFields")
 void valuesExceedingDatabaseColumnLengthsAreRejected(String url, String field, int maxLength) throws Exception {
  var request = post(url).principal(ADMIN);
  if (!field.equals("name")) request.param("name", "테스트 살롱");
  if (!field.equals("address")) request.param("address", "서울 강남구");
  mockMvc.perform(request.param(field, "가".repeat(maxLength + 1)))
    .andExpect(status().isOk()).andExpect(view().name("salon/form"))
    .andExpect(model().attributeHasFieldErrors("form", field));
  verifyNoInteractions(salonQueryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salons/7/edit"})
 void invalidBooleanBindingReturnsFieldErrorInsteadOfSaving(String url) throws Exception {
  MvcResult result = mockMvc.perform(post(url).principal(ADMIN).param("name", "테스트 살롱")
      .param("address", "서울 강남구").param("reservable", "invalid"))
    .andExpect(status().isOk()).andExpect(view().name("salon/form"))
    .andExpect(model().attributeHasFieldErrors("form", "reservable")).andReturn();
  assertThat(bindingResult(result).getFieldError("reservable").getCode()).isEqualTo("typeMismatch");
  verifyNoInteractions(salonQueryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salons/7/edit"})
 void explicitlyEmptyBooleanIsRejected(String url) throws Exception {
  mockMvc.perform(post(url).principal(ADMIN).param("name", "테스트 살롱")
      .param("address", "서울 강남구").param("reservable", ""))
    .andExpect(status().isOk()).andExpect(view().name("salon/form"))
    .andExpect(model().attributeHasFieldErrors("form", "reservable"));
  verifyNoInteractions(salonQueryService);
 }

 @Test
 void validCreateAtColumnLimitsKeepsDefaultReservableAndRedirects() throws Exception {
  when(salonQueryService.create(any(SalonCreateRequest.class))).thenReturn(7);
  mockMvc.perform(post("/salons").principal(ADMIN).param("name", "가".repeat(100))
      .param("address", "가".repeat(255)).param("roadAddress", "가".repeat(255))
      .param("phone", "1".repeat(20)).param("imageUrl", "x".repeat(255))
      .param("placeUrl", "x".repeat(255)).param("externalId", "x".repeat(100))
      .param("sourceType", "x".repeat(30)))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salons/7"));
  verify(salonQueryService).create(argThat(request -> Boolean.TRUE.equals(request.getReservable())
    && request.getName().length() == 100 && request.getAddress().length() == 255));
 }

 @Test
 void validUpdateAcceptsUnavailableReservationAndRedirects() throws Exception {
  mockMvc.perform(post("/salons/7/edit").principal(ADMIN).param("name", "수정 살롱")
      .param("address", "서울 강남구").param("reservable", "false"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salons/7"));
  verify(salonQueryService).update(eq(7), argThat((SalonUpdateRequest request) ->
    "수정 살롱".equals(request.getName()) && Boolean.FALSE.equals(request.getReservable())));
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salons", "/salons/7/edit"})
 void validationDoesNotBypassAdminGuard(String url) throws Exception {
  mockMvc.perform(post(url)).andExpect(status().is3xxRedirection())
    .andExpect(redirectedUrl("/members/login"));
  Authentication member = new UsernamePasswordAuthenticationToken("member", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  mockMvc.perform(post(url).principal(member)).andExpect(status().is3xxRedirection())
    .andExpect(redirectedUrl("/access-denied"));
  verifyNoInteractions(salonQueryService);
 }

 static Stream<RuntimeException> externalFailures() {
  return Stream.of(new ResourceAccessException("private connection detail"),
    new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "private status detail"),
    new IllegalStateException("private response parsing detail"));
 }

 @ParameterizedTest
 @MethodSource("externalFailures")
 void externalFailurePreservesInternalResultsAndMarkers(RuntimeException failure) throws Exception {
  SalonSummaryResponse internal = stubInternalSearchAndMarker();
  when(kakaoLocalSearchClient.searchSalons("커트", "서울", 1, 15)).thenThrow(failure);
  MvcResult result = mockMvc.perform(get("/salons").param("keyword", "커트").param("region", "서울"))
    .andExpect(status().isOk()).andExpect(view().name("salon/list"))
    .andExpect(model().attribute("salons", List.of(internal)))
    .andExpect(model().attribute("kakaoResults", List.of()))
    .andExpect(model().attribute("kakaoResultsMessage", TEMPORARY_FAILURE)).andReturn();
  assertThat(markers(result)).extracting(SalonBranchMarkerResponse::getMarkerKey).containsExactly("internal-7");
 }

 @Test
 void successfulExternalSearchStillMergesBothMarkerSourcesAndFiltersRating() throws Exception {
  stubInternalSearchAndMarker();
  KakaoPlaceSearchResult matching = KakaoPlaceSearchResult.builder().externalId("k1").placeName("외부 살롱")
    .longitude(new BigDecimal("127.03")).latitude(new BigDecimal("37.5")).averageRating(new BigDecimal("4.9")).build();
  KakaoPlaceSearchResult belowMinimum = matching.toBuilder().externalId("k2").averageRating(new BigDecimal("4.1")).build();
  when(kakaoLocalSearchClient.searchSalons("커트", "서울", 1, 15)).thenReturn(List.of(matching, belowMinimum));
  MvcResult result = mockMvc.perform(get("/salons").param("keyword", "커트").param("region", "서울").param("minRating", "4.5"))
    .andExpect(status().isOk()).andExpect(view().name("salon/list"))
    .andExpect(model().attribute("kakaoResults", List.of(matching)))
    .andExpect(model().attribute("kakaoResultsMessage", nullValue())).andReturn();
  assertThat(markers(result)).extracting(SalonBranchMarkerResponse::getMarkerKey).containsExactly("internal-7", "kakao-k1");
 }

 @Test
 void emptyExternalSearchIsNotPresentedAsAnOutage() throws Exception {
  stubInternalSearchAndMarker();
  when(kakaoLocalSearchClient.searchSalons("커트", "서울", 1, 15)).thenReturn(List.of());
  mockMvc.perform(get("/salons").param("keyword", "커트").param("region", "서울"))
    .andExpect(status().isOk()).andExpect(view().name("salon/list"))
    .andExpect(model().attribute("kakaoResults", List.of()))
    .andExpect(model().attribute("kakaoResultsMessage", nullValue()));
 }

 private SalonSummaryResponse stubInternalSearchAndMarker() {
  SalonSummaryResponse internal = SalonSummaryResponse.builder().salonId(7).name("내부 살롱")
    .address("서울 강남구").roadAddress("서울 강남구 테헤란로 1").reservable(true).build();
  when(salonQueryService.search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(internal)));
  when(kakaoLocalSearchClient.isConfigured()).thenReturn(true);
  when(kakaoLocalSearchClient.searchAddress(internal.getRoadAddress())).thenReturn(Optional.of(
    KakaoAddressSearchResult.builder().longitude(new BigDecimal("127.0")).latitude(new BigDecimal("37.5")).build()));
  return internal;
 }

 private BindingResult bindingResult(MvcResult result) {
  return (BindingResult) result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "form");
 }

 @SuppressWarnings("unchecked")
 private List<SalonBranchMarkerResponse> markers(MvcResult result) {
  return (List<SalonBranchMarkerResponse>) result.getModelAndView().getModel().get("branchMarkers");
 }
}

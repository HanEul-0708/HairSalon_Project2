package com.hairsalonproject2.salonservice.controller;

import com.hairsalonproject2.common.catalog.CatalogConflictException;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.stream.Stream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ServiceControllerValidationTest {
 @Mock private SalonServiceQueryService queryService;
 @InjectMocks private ServiceController controller;
 private MockMvc mockMvc;

 @BeforeEach
 void setUp() {
  InternalResourceViewResolver resolver = new InternalResourceViewResolver();
  resolver.setPrefix("/templates/");
  resolver.setSuffix(".html");
  mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
 }

 @ParameterizedTest
 @MethodSource("invalidFields")
 void invalidCreateAndUpdateNeverWriteAndRetainRejectedValue(String path, String field, String value) throws Exception {
  if (!"salonId".equals(field)) when(queryService.existsSalon(7)).thenReturn(true);
  MockHttpServletRequestBuilder request = validRequest(path);
  // Replace, rather than append, the parameter so Spring binds the invalid value.
  request.with(servletRequest -> {
   servletRequest.setParameter(field, value);
   return servletRequest;
  });

  var result = mockMvc.perform(request).andExpect(status().isOk()).andExpect(view().name("service/form"))
          .andExpect(model().attributeHasFieldErrors("form", field)).andReturn();

  BindingResult errors = (BindingResult) result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "form");
  assertThat(errors.getFieldValue(field)).hasToString(value);
  if (path.endsWith("/edit")) assertThat(result.getModelAndView().getModel()).containsEntry("serviceId", 12);
  verify(queryService, never()).create(any());
  verify(queryService, never()).update(anyInt(), any());
 }

 static Stream<Arguments> invalidFields() {
  return Stream.of("/salon-services", "/salon-services/12/edit").flatMap(path -> Stream.of(
          Arguments.of(path, "salonId", "0"),
          Arguments.of(path, "salonId", "-1"),
          Arguments.of(path, "salonId", "abc"),
          Arguments.of(path, "name", "   "),
          Arguments.of(path, "name", "가".repeat(101)),
          Arguments.of(path, "price", "-1"),
          Arguments.of(path, "price", "1.5"),
          Arguments.of(path, "price", "2147483648"),
          Arguments.of(path, "duration", "0"),
          Arguments.of(path, "duration", "-1"),
          Arguments.of(path, "duration", "abc")));
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salon-services", "/salon-services/12/edit"})
 void missingRequiredFieldsNeverWrite(String path) throws Exception {
  mockMvc.perform(post(path)).andExpect(status().isOk()).andExpect(view().name("service/form"))
          .andExpect(model().attributeHasFieldErrors("form", "salonId", "name", "price", "duration"));

  verifyNoInteractions(queryService);
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salon-services", "/salon-services/12/edit"})
 void nonexistentSalonIsAFieldErrorAndRetainsTheSubmittedForm(String path) throws Exception {
  var result = mockMvc.perform(validRequest(path)).andExpect(status().isOk())
          .andExpect(view().name("service/form")).andExpect(model().attributeHasFieldErrorCode("form", "salonId", "salon.notFound"))
          .andReturn();

  BindingResult errors = (BindingResult) result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "form");
  assertThat(errors.getFieldValue("salonId")).hasToString("7");
  assertThat(errors.getFieldValue("name")).isEqualTo("테스트 시술");
  assertThat(errors.getFieldValue("price")).hasToString("0");
  assertThat(errors.getFieldValue("duration")).hasToString("1");
  assertThat(errors.getFieldValue("description")).isEqualTo("입력한 설명");
  if (path.endsWith("/edit")) assertThat(result.getModelAndView().getModel()).containsEntry("serviceId", 12);
  verify(queryService, never()).create(any());
  verify(queryService, never()).update(anyInt(), any());
 }

 @ParameterizedTest
 @ValueSource(strings = {"/salon-services", "/salon-services/12/edit"})
 void validBoundaryValuesAreSavedAndRedirectToDetail(String path) throws Exception {
  when(queryService.existsSalon(7)).thenReturn(true);
  if (!path.endsWith("/edit")) when(queryService.create(any())).thenReturn(12);
  String name = "가".repeat(100);

  mockMvc.perform(validRequest(path).with(request -> {
   request.setParameter("name", name);
   return request;
  })).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salon-services/12"));

  if (path.endsWith("/edit")) {
   ArgumentCaptor<SalonServiceUpdateRequest> captor = ArgumentCaptor.forClass(SalonServiceUpdateRequest.class);
   verify(queryService).update(eq(12), captor.capture());
   assertThat(captor.getValue().getSalonId()).isEqualTo(7);
   assertThat(captor.getValue().getName()).isEqualTo(name);
   assertThat(captor.getValue().getPrice()).isZero();
   assertThat(captor.getValue().getDuration()).isEqualTo(1);
  } else {
   ArgumentCaptor<SalonServiceCreateRequest> captor = ArgumentCaptor.forClass(SalonServiceCreateRequest.class);
   verify(queryService).create(captor.capture());
   assertThat(captor.getValue().getSalonId()).isEqualTo(7);
   assertThat(captor.getValue().getName()).isEqualTo(name);
   assertThat(captor.getValue().getPrice()).isZero();
   assertThat(captor.getValue().getDuration()).isEqualTo(1);
  }
 }

 private MockHttpServletRequestBuilder validRequest(String path) {
  return post(path).param("salonId", "7").param("name", "테스트 시술")
          .param("price", "0").param("duration", "1").param("description", "입력한 설명");
 }

 @ParameterizedTest
 @MethodSource("invalidSearchFields")
 void invalidSearchNeverQueriesAndRetainsInput(String path, String field, String value) throws Exception {
  var result = mockMvc.perform(get(path).param(field, value))
          .andExpect(status().isOk())
          .andExpect(view().name(path.endsWith("compare") ? "service/compare" : "service/list"))
          .andExpect(model().attributeHasFieldErrors("search", field)).andReturn();

  BindingResult errors = (BindingResult) result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "search");
  if (value.isEmpty()) assertThat(errors.getFieldValue(field)).isIn(null, "");
  else assertThat(errors.getFieldValue(field)).hasToString(value);
  verifyNoInteractions(queryService);
 }

 static Stream<Arguments> invalidSearchFields() {
  Stream<Arguments> lists = Stream.of("/salon-services", "/salon-services/popular", "/salon-services/trend").flatMap(path -> Stream.of(
          Arguments.of(path, "maxPrice", "abc"), Arguments.of(path, "maxPrice", "2147483648"),
          Arguments.of(path, "maxPrice", "-1"), Arguments.of(path, "maxDuration", "abc"),
          Arguments.of(path, "maxDuration", "2147483648"), Arguments.of(path, "maxDuration", "0")));
  Stream<Arguments> pages = Stream.of("/salon-services", "/salon-services/popular", "/salon-services/trend", "/salon-services/compare")
          .flatMap(path -> Stream.of("abc", "2147483648", "0", "-1", "")
                  .map(value -> Arguments.of(path, "page", value)));
  return Stream.concat(lists, pages);
 }

 @Test
 void comparisonUsesRequestedPageAndReturnsTotalCount() throws Exception {
  when(queryService.compare("커트", "서울", 2, 15))
          .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 15), 31));
  mockMvc.perform(get("/salon-services/compare").param("serviceName", "커트").param("region", "서울").param("page", "3"))
          .andExpect(status().isOk()).andExpect(model().attribute("totalComparisonCount", 31L));
  verify(queryService).compare("커트", "서울", 2, 15);
 }

 @Test
 void deletePostUsesGuardedServiceAndRedirects() throws Exception {
  mockMvc.perform(post("/salon-services/12/delete"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salon-services"));
  verify(queryService).delete(12);
 }

 @Test
 void deletionConflictReturnsToDetailWithMessage() throws Exception {
  String message = "예약 이력이 있는 시술은 삭제할 수 없습니다.";
  doThrow(new CatalogConflictException(null, message)).when(queryService).delete(12);
  mockMvc.perform(post("/salon-services/12/delete"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/salon-services/12"))
          .andExpect(flash().attribute("message", message));
 }

 @Test
 void salonTransferConflictRendersTheSubmittedFormWithFieldError() throws Exception {
  when(queryService.existsSalon(7)).thenReturn(true);
  doThrow(new CatalogConflictException("salonId", "예약 이력이 있는 시술은 미용실을 변경할 수 없습니다."))
          .when(queryService).update(eq(12), any());
  mockMvc.perform(validRequest("/salon-services/12/edit"))
          .andExpect(status().isOk()).andExpect(view().name("service/form"))
          .andExpect(model().attributeHasFieldErrorCode("form", "salonId", "catalog.conflict"))
          .andExpect(model().attribute("serviceId", 12));
 }
}

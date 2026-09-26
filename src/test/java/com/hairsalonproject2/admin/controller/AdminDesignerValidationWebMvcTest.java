package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.designer.dto.request.DesignerCreateRequest;
import com.hairsalonproject2.designer.dto.request.DesignerUpdateRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminDesignerValidationWebMvcTest {

 private static final String CREATE_URL = "/admin/designers";
 private static final String EDIT_URL = "/admin/designers/7/edit";

 @Mock
 private DesignerQueryService designerQueryService;

 private MockMvc mockMvc;

 @BeforeEach
 void setUp() {
  InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
  viewResolver.setPrefix("/templates/");
  viewResolver.setSuffix(".html");
  mockMvc = MockMvcBuilders.standaloneSetup(new AdminDesignerController(designerQueryService))
   .setViewResolvers(viewResolver).build();
  lenient().when(designerQueryService.existsSalon(1)).thenReturn(true);
 }

 @ParameterizedTest
 @MethodSource("invalidInputs")
 void invalidInputReturnsFieldErrorWithoutSaving(String url, String field, String value, String code) throws Exception {
  var params = validParams();
  params.set(field, value);

  ResultActions result = mockMvc.perform(post(url).params(params))
   .andExpect(status().isOk())
   .andExpect(view().name("designer/form"))
   .andExpect(model().attributeHasFieldErrorCode("form", field, code));
  if (EDIT_URL.equals(url)) result.andExpect(model().attribute("designerId", 7));
  verifyNoSave();
 }

 @ParameterizedTest
 @ValueSource(strings = {CREATE_URL, EDIT_URL})
 void missingSalonIsRejectedWithoutLookingItUp(String url) throws Exception {
  var params = validParams();
  params.remove("salonId");

  mockMvc.perform(post(url).params(params))
   .andExpect(status().isOk())
   .andExpect(view().name("designer/form"))
   .andExpect(model().attributeHasFieldErrorCode("form", "salonId", "NotNull"));

  verify(designerQueryService, never()).existsSalon(any());
  verifyNoSave();
 }

 @ParameterizedTest
 @ValueSource(strings = {CREATE_URL, EDIT_URL})
 void missingCareerYearsReturnsFieldErrorWithoutSaving(String url) throws Exception {
  var params = validParams();
  params.remove("careerYears");

  ResultActions result = mockMvc.perform(post(url).params(params))
   .andExpect(status().isOk())
   .andExpect(view().name("designer/form"))
   .andExpect(model().attributeHasFieldErrorCode("form", "careerYears", "NotNull"));
  if (EDIT_URL.equals(url)) result.andExpect(model().attribute("designerId", 7));
  verifyNoSave();
 }

 @ParameterizedTest
 @ValueSource(strings = {CREATE_URL, EDIT_URL})
 void unknownSalonReturnsSubmittedFormWithFieldError(String url) throws Exception {
  var params = validParams();
  params.set("salonId", "404");

  ResultActions result = mockMvc.perform(post(url).params(params))
   .andExpect(status().isOk())
   .andExpect(view().name("designer/form"))
   .andExpect(model().attributeHasFieldErrorCode("form", "salonId", "NotFound"))
   .andExpect(model().attribute("form", hasProperty("name", is("디자이너"))))
   .andExpect(model().attribute("form", hasProperty("salonId", is(404))));
  if (EDIT_URL.equals(url)) result.andExpect(model().attribute("designerId", 7));
  verify(designerQueryService).existsSalon(404);
  verifyNoSave();
 }

 @ParameterizedTest
 @ValueSource(strings = {CREATE_URL, EDIT_URL})
 void validInputAcceptsZeroExperienceAndOptionalFields(String url) throws Exception {
  if (CREATE_URL.equals(url)) when(designerQueryService.create(any())).thenReturn(7);

  mockMvc.perform(post(url).params(validParams()))
   .andExpect(status().is3xxRedirection())
   .andExpect(redirectedUrl("/designers/7"));

  if (CREATE_URL.equals(url)) {
   verify(designerQueryService).create(argThat(request -> request.getSalonId() == 1
    && request.getCareerYears() == 0 && "디자이너".equals(request.getName())));
  } else {
   verify(designerQueryService).update(eq(7), argThat(request -> request.getSalonId() == 1
    && request.getCareerYears() == 0 && "디자이너".equals(request.getName())));
  }
 }

 private void verifyNoSave() {
  verify(designerQueryService, never()).create(any(DesignerCreateRequest.class));
  verify(designerQueryService, never()).update(anyInt(), any(DesignerUpdateRequest.class));
 }

 private static LinkedMultiValueMap<String, String> validParams() {
  var params = new LinkedMultiValueMap<String, String>();
  params.set("salonId", "1");
  params.set("name", "디자이너");
  params.set("careerYears", "0");
  return params;
 }

 private static Stream<Arguments> invalidInputs() {
  return Stream.of(CREATE_URL, EDIT_URL).flatMap(url -> Stream.of(
   Arguments.of(url, "name", "   ", "NotBlank"),
   Arguments.of(url, "name", "가".repeat(51), "Size"),
   Arguments.of(url, "salonId", "", "NotNull"),
   Arguments.of(url, "salonId", "0", "Positive"),
   Arguments.of(url, "salonId", "-1", "Positive"),
   Arguments.of(url, "salonId", "abc", "typeMismatch"),
   Arguments.of(url, "careerYears", "", "NotNull"),
   Arguments.of(url, "careerYears", "-1", "Min"),
   Arguments.of(url, "careerYears", "abc", "typeMismatch"),
   Arguments.of(url, "profileImage", "x".repeat(256), "Size"),
   Arguments.of(url, "memberId", "x".repeat(31), "Size")
  ));
 }
}

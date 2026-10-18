package com.hairsalonproject2.common.layout;

import com.hairsalonproject2.common.config.SecurityConfig;
import com.hairsalonproject2.member.controller.MemberController;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import com.hairsalonproject2.member.service.MemberService;
import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class FooterLayoutRenderingTest {
 @Autowired private MockMvc mvc;
 @MockitoBean private MemberService memberService;
 @MockitoBean private CustomUserDetailsService userDetailsService;
 @MockitoBean private JpaMetamodelMappingContext metamodel;

 @ParameterizedTest
 @CsvSource({
  "/members/login, member/login, login.html",
  "/members/signup, member/signup, signup.html"
 })
 void standaloneMemberPageRendersSharedFooterAndBrowserFixture(String path, String template, String fixture) throws Exception {
  var result = mvc.perform(get(path))
   .andExpect(status().isOk()).andExpect(view().name(template)).andReturn();
  String rendered = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  var html = Jsoup.parse(rendered);

  assertThat(html.select(".simple-header")).hasSize(1);
  assertThat(html.select("body > footer.site-footer")).hasSize(1);
  assertThat(html.select("footer.site-footer .footer__gold-bar")).hasSize(1);
  assertThat(html.select("footer.site-footer .footer__inner")).hasSize(1);
  assertThat(html.select("footer.site-footer .footer__bottom")).hasSize(1);
  assertThat(html.select("footer.site-footer a[href=/designers]")).hasSize(1);
  assertThat(html.select("link[href=/css/common.css]")).isNotEmpty();
  assertThat(html.select("script[src=/js/common.js]")).isNotEmpty();
  assertThat(html.select("main")).hasSize(1);
  verifyNoInteractions(memberService, userDetailsService);

  Path fixtureDirectory = Path.of("build", "catalog-layout", "fixtures");
  Files.createDirectories(fixtureDirectory);
  Files.writeString(fixtureDirectory.resolve(fixture), rendered, StandardCharsets.UTF_8);
 }
}

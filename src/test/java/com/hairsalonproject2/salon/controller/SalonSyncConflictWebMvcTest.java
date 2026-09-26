package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.salon.service.ExternalSalonSyncConflictException;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SalonSyncConflictWebMvcTest {
 @Mock SalonQueryService salons;
 @Mock ExternalSalonSyncService sync;
 @Mock KakaoLocalSearchClient kakao;
 @InjectMocks SalonController controller;

 @Test
 void exhaustedConcurrentSyncRedirectsWithRetryMessageInsteadOfServerError() throws Exception {
  var failure = new ExternalSalonSyncConflictException(new DuplicateKeyException("duplicate"));
  when(kakao.isConfigured()).thenReturn(true);
  when(sync.syncFromKakao("hair", "Seoul")).thenThrow(failure);

  MockMvcBuilders.standaloneSetup(controller).build().perform(post("/salons/sync/kakao")
      .param("keyword", "hair").param("region", "Seoul")
      .principal(new UsernamePasswordAuthenticationToken("admin", "n/a",
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
    .andExpect(status().is3xxRedirection())
    .andExpect(redirectedUrl("/salons?keyword=hair&region=Seoul"))
    .andExpect(flash().attribute("message", failure.getMessage()));
 }
}

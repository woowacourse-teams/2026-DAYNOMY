package org.grit.daynomy.emailsubscription.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.CommonErrorCode;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.emailsubscription.service.EmailUnsubscribeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = EmailUnsubscribeController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmailUnsubscribeControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private EmailUnsubscribeService service;

  @Test
  void getShowsAnEscapedConfirmationAndDoesNotUnsubscribe() throws Exception {
    mvc.perform(get(EmailUnsubscribeService.PATH).param("token", "signed\"<token>"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("signed&quot;&lt;token&gt;")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")));
    verify(service).validate("signed\"<token>");
    verify(service, never()).unsubscribe(anyString());
  }

  @Test
  void postUsesOnlySignedTokenAndReturnsNoAccountData() throws Exception {
    mvc.perform(post(EmailUnsubscribeService.PATH).param("token", "signed-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("요청을 처리했습니다")));
    verify(service).unsubscribe("signed-token");
  }

  @Test
  void invalidAndMissingTokensUseExistingBadRequestContract() throws Exception {
    doThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST))
        .when(service)
        .unsubscribe("tampered");
    mvc.perform(post(EmailUnsubscribeService.PATH).param("token", "tampered"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(post(EmailUnsubscribeService.PATH)).andExpect(status().isBadRequest());
  }
}

package org.grit.daynomy.emailsubscription.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.dto.EmailSubscriptionResponse;
import org.grit.daynomy.emailsubscription.service.EmailSubscriptionService;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = EmailSubscriptionController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class EmailSubscriptionControllerTest {

  private static final String PATH = "/api/users/me/email-subscription";
  @Autowired private MockMvc mvc;
  @MockitoBean private EmailSubscriptionService service;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(3L, MemberRole.USER), null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getReturnsStatusAccountEmailAndInvitationFlag() throws Exception {
    when(service.get(3L))
        .thenReturn(
            new EmailSubscriptionResponse(
                EmailSubscriptionStatus.UNDECIDED, "google@example.invalid", true, null));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNDECIDED"))
        .andExpect(jsonPath("$.email").value("google@example.invalid"))
        .andExpect(jsonPath("$.promptRequired").value(true))
        .andExpect(jsonPath("$.subscribedAt").isEmpty());
  }

  @Test
  void subscribeNeedsNoEmailRequestBody() throws Exception {
    when(service.subscribe(3L))
        .thenReturn(
            new EmailSubscriptionResponse(
                EmailSubscriptionStatus.SUBSCRIBED,
                "google@example.invalid",
                false,
                Instant.parse("2026-10-19T00:00:00Z")));

    mvc.perform(put(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBSCRIBED"))
        .andExpect(jsonPath("$.promptRequired").value(false))
        .andExpect(jsonPath("$.subscribedAt").value("2026-10-19T00:00:00Z"));
  }

  @Test
  void aMissingMemberUsesTheExistingErrorContract() throws Exception {
    when(service.get(3L)).thenThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

    mvc.perform(get(PATH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }
}

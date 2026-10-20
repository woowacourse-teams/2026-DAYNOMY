package org.grit.daynomy.content.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentInteractionType;
import org.grit.daynomy.content.domain.ContentType;
import org.grit.daynomy.content.dto.ContentInteractionRequest;
import org.grit.daynomy.content.dto.ContentPopularityItemResponse;
import org.grit.daynomy.content.dto.ContentPopularityResponse;
import org.grit.daynomy.content.service.ContentInteractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = ContentInteractionController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class ContentInteractionControllerTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @MockitoBean private ContentInteractionService service;

  @Test
  void recordsValidInteraction() throws Exception {
    ContentInteractionRequest request =
        new ContentInteractionRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ContentType.NEWS,
            1L,
            ContentInteractionType.OPEN);

    mockMvc
        .perform(
            post("/api/content-interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(service).should().record(request);
  }

  @Test
  void rejectsInvalidInteraction() throws Exception {
    mockMvc
        .perform(
            post("/api/content-interactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void returnsPopularityForRequestedPeriod() throws Exception {
    given(service.getPopularity(7))
        .willReturn(
            new ContentPopularityResponse(
                List.of(
                    new ContentPopularityItemResponse(
                        ContentType.NEWS, 1L, 4L, Instant.parse("2026-10-20T02:00:00Z"))),
                7));

    mockMvc
        .perform(get("/api/content-rankings").param("periodDays", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].contentType").value("NEWS"))
        .andExpect(jsonPath("$.items[0].score").value(4))
        .andExpect(jsonPath("$.periodDays").value(7));
  }

  @Test
  void rejectsPopularityPeriodOutsideRange() throws Exception {
    mockMvc
        .perform(get("/api/content-rankings").param("periodDays", "31"))
        .andExpect(status().isBadRequest());
    then(service).shouldHaveNoInteractions();
  }
}

package org.grit.daynomy.feedback.controller;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.feedback.dto.FeedbackCreateRequest;
import org.grit.daynomy.feedback.service.FeedbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = FeedbackController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeedbackControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeedbackService feedbackService;

  @Test
  @DisplayName("피드백 등록 API는 내용을 저장하고 204를 반환한다")
  void createFeedbackReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            post("/api/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content":"화면이 좋아요.","pagePath":"/news/1"}
                    """))
        .andExpect(status().isNoContent());

    then(feedbackService).should().create(new FeedbackCreateRequest("화면이 좋아요.", "/news/1"));
  }

  @Test
  @DisplayName("피드백 등록 API는 빈 내용을 거부한다")
  void createFeedbackRejectsBlankContent() throws Exception {
    mockMvc
        .perform(
            post("/api/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content":"  ","pagePath":"/news/1"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    then(feedbackService).shouldHaveNoInteractions();
  }
}

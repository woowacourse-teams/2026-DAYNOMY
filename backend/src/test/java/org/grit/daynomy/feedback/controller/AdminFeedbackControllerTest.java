package org.grit.daynomy.feedback.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.feedback.domain.FeedbackCategory;
import org.grit.daynomy.feedback.domain.FeedbackStatus;
import org.grit.daynomy.feedback.dto.AdminFeedbackCreateRequest;
import org.grit.daynomy.feedback.dto.AdminFeedbackPageResponse;
import org.grit.daynomy.feedback.dto.AdminFeedbackResponse;
import org.grit.daynomy.feedback.dto.FeedbackStatusUpdateRequest;
import org.grit.daynomy.feedback.service.FeedbackService;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = AdminFeedbackController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class AdminFeedbackControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeedbackService feedbackService;

  @BeforeEach
  void setUpSecurityContext() {
    AuthenticatedMember member = new AuthenticatedMember(7L, MemberRole.ADMIN);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("관리자 피드백 등록 API는 인증 회원과 요청 내용을 저장하고 204를 반환한다")
  void createFeedbackReturnsNoContent() throws Exception {
    AdminFeedbackCreateRequest request =
        new AdminFeedbackCreateRequest(
            FeedbackCategory.BUG, "저장 버튼이 동작하지 않아요.", "/admin/news/new");

    mockMvc
        .perform(
            post("/api/admin/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category":"BUG",
                      "content":"저장 버튼이 동작하지 않아요.",
                      "pagePath":"/admin/news/new"
                    }
                    """))
        .andExpect(status().isNoContent());

    then(feedbackService).should().createAdmin(7L, request);
  }

  @Test
  @DisplayName("관리자 피드백 등록 API는 유형이 없으면 요청을 거부한다")
  void createFeedbackRejectsMissingCategory() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content":"저장 버튼이 동작하지 않아요.","pagePath":"/admin/news/new"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    then(feedbackService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("관리자 피드백 목록 API는 페이지 응답을 반환한다")
  void getFeedbacksReturnsPage() throws Exception {
    Instant now = Instant.parse("2026-10-12T09:00:00Z");
    given(feedbackService.getPage(1, 15))
        .willReturn(
            new AdminFeedbackPageResponse(
                List.of(
                    new AdminFeedbackResponse(
                        1L,
                        "화면이 좋아요.",
                        "/news/1",
                        null,
                        null,
                        null,
                        FeedbackStatus.PENDING,
                        now,
                        now)),
                1,
                15,
                1,
                1,
                false));

    mockMvc
        .perform(get("/api/admin/feedbacks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].content").value("화면이 좋아요."))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("관리자 피드백 상태 수정 API는 변경된 상태를 반환한다")
  void updateFeedbackStatusReturnsUpdatedFeedback() throws Exception {
    Instant now = Instant.parse("2026-10-12T09:00:00Z");
    FeedbackStatusUpdateRequest request = new FeedbackStatusUpdateRequest(FeedbackStatus.RESOLVED);
    given(feedbackService.updateStatus(1L, request))
        .willReturn(
            new AdminFeedbackResponse(
                1L,
                "화면이 좋아요.",
                "/news/1",
                null,
                null,
                null,
                FeedbackStatus.RESOLVED,
                now,
                now));

    mockMvc
        .perform(
            patch("/api/admin/feedbacks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status":"RESOLVED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));

    then(feedbackService).should().updateStatus(1L, request);
  }

  @Test
  @DisplayName("관리자 피드백 삭제 API는 204를 반환한다")
  void deleteFeedbackReturnsNoContent() throws Exception {
    mockMvc.perform(delete("/api/admin/feedbacks/1")).andExpect(status().isNoContent());

    then(feedbackService).should().delete(1L);
  }
}

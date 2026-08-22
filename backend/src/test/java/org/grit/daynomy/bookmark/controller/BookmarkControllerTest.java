package org.grit.daynomy.bookmark.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.bookmark.dto.BookmarkResponse;
import org.grit.daynomy.bookmark.service.BookmarkService;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class BookmarkControllerTest {

  private BookmarkService bookmarkService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    bookmarkService = mock(BookmarkService.class);

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    MethodValidationPostProcessor methodValidationPostProcessor =
        new MethodValidationPostProcessor();
    methodValidationPostProcessor.setValidator(validator);
    methodValidationPostProcessor.afterPropertiesSet();

    Object controller =
        methodValidationPostProcessor.postProcessAfterInitialization(
            new BookmarkController(bookmarkService), "bookmarkController");

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void addsBookmarkForAuthenticatedMember() throws Exception {
    when(bookmarkService.addBookmark(eq(3L), eq(10L)))
        .thenReturn(new BookmarkResponse(1L, 10L));

    mockMvc
        .perform(
            post("/api/assets/bookmarks")
                .principal(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(3L, MemberRole.USER), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\":10}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.targetId").value(10));

    verify(bookmarkService).addBookmark(3L, 10L);
  }

  @Test
  void deletesBookmarkForAuthenticatedMember() throws Exception {
    mockMvc
        .perform(
            delete("/api/assets/bookmarks")
                .principal(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(3L, MemberRole.USER), null))
                .param("targetId", "10"))
        .andExpect(status().isNoContent());

    verify(bookmarkService).deleteBookmark(3L, 10L);
  }

  @Test
  void rejectsNonPositiveTargetIdWhenDeleting() throws Exception {
    mockMvc
        .perform(
            delete("/api/assets/bookmarks")
                .principal(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(3L, MemberRole.USER), null))
                .param("targetId", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"));

    verifyNoInteractions(bookmarkService);
  }

  @Test
  void rejectsMissingTargetId() throws Exception {
    mockMvc
        .perform(
            post("/api/assets/bookmarks")
                .principal(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(3L, MemberRole.USER), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"));
  }
}

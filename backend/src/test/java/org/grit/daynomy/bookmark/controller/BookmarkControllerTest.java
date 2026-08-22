package org.grit.daynomy.bookmark.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.grit.daynomy.bookmark.service.BookmarkService;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookmarkControllerTest {

  private BookmarkService bookmarkService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    bookmarkService = mock(BookmarkService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new BookmarkController(bookmarkService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void addsBookmarkForAuthenticatedMember() throws Exception {
    Bookmark bookmark = mock(Bookmark.class);
    when(bookmark.getId()).thenReturn(1L);
    when(bookmark.getTargetId()).thenReturn(10L);
    when(bookmarkService.addBookmark(eq(3L), eq(10L))).thenReturn(bookmark);

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

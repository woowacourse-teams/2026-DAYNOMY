package org.grit.daynomy.bookmark.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.bookmark.dto.BookmarkResponse;
import org.grit.daynomy.bookmark.service.BookmarkService;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = BookmarkController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class BookmarkControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BookmarkService bookmarkService;

  @BeforeEach
  void setUpSecurityContext() {
    AuthenticatedMember member = new AuthenticatedMember(3L, MemberRole.USER);
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(member, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("인증 회원의 자산을 북마크한다")
  void addsBookmarkForAuthenticatedMember() throws Exception {
    when(bookmarkService.addBookmark(eq(3L), eq(10L)))
        .thenReturn(new BookmarkResponse(1L, 10L, "에코프로비엠"));

    mockMvc
        .perform(
            post("/api/assets/bookmarks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\":10}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.targetId").value(10))
        .andExpect(jsonPath("$.assetName").value("에코프로비엠"));

    verify(bookmarkService).addBookmark(3L, 10L);
  }

  @Test
  @DisplayName("인증 회원의 북마크를 삭제한다")
  void deletesBookmarkForAuthenticatedMember() throws Exception {
    mockMvc
        .perform(delete("/api/assets/bookmarks").param("targetId", "10"))
        .andExpect(status().isNoContent());

    verify(bookmarkService).deleteBookmark(3L, 10L);
  }

  @Test
  @DisplayName("삭제 자산 ID가 양수가 아니면 요청을 거부한다")
  void rejectsNonPositiveTargetIdWhenDeleting() throws Exception {
    mockMvc
        .perform(delete("/api/assets/bookmarks").param("targetId", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"));

    verifyNoInteractions(bookmarkService);
  }

  @Test
  @DisplayName("인증 회원의 북마크 목록을 반환한다")
  void returnsBookmarksForAuthenticatedMember() throws Exception {
    when(bookmarkService.getBookmarks(3L))
        .thenReturn(
            List.of(
                new BookmarkResponse(1L, 10L, "에코프로비엠"), new BookmarkResponse(2L, 20L, "삼성전자")));

    mockMvc
        .perform(get("/api/users/me/bookmarks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].targetId").value(10))
        .andExpect(jsonPath("$[0].assetName").value("에코프로비엠"))
        .andExpect(jsonPath("$[1].id").value(2))
        .andExpect(jsonPath("$[1].targetId").value(20))
        .andExpect(jsonPath("$[1].assetName").value("삼성전자"));

    verify(bookmarkService).getBookmarks(3L);
  }

  @Test
  @DisplayName("북마크 추가 요청에 자산 ID가 없으면 요청을 거부한다")
  void rejectsMissingTargetId() throws Exception {
    mockMvc
        .perform(
            post("/api/assets/bookmarks").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"));
  }
}

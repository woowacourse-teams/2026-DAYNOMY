package org.grit.daynomy.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.service.MemberService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = MemberController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class MemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberService memberService;

  @MockitoBean private TokenCookieManager tokenCookieManager;

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
  @DisplayName("인증 회원의 정보를 응답 DTO로 반환한다")
  void getMeReturnsAuthenticatedMember() throws Exception {
    Member member = createMember(3L, "member@example.com", "daynomy");
    when(memberService.getMember(3L)).thenReturn(member);

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.email").value("member@example.com"))
        .andExpect(jsonPath("$.nickname").value("daynomy"))
        .andExpect(jsonPath("$.role").value("USER"));

    verify(memberService).getMember(3L);
  }

  @Test
  @DisplayName("닉네임 수정 요청을 인증 회원 ID와 함께 전달한다")
  void updateMeUpdatesAuthenticatedMemberNickname() throws Exception {
    Member member = createMember(3L, "member@example.com", "새닉네임");
    when(memberService.updateNickname(3L, "새닉네임")).thenReturn(member);

    mockMvc
        .perform(
            patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.nickname").value("새닉네임"));

    verify(memberService).updateNickname(3L, "새닉네임");
  }

  @Test
  @DisplayName("닉네임이 비어 있으면 수정 요청을 거부한다")
  void updateMeRejectsBlankNickname() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("nickname"));

    verifyNoInteractions(memberService);
  }

  @Test
  @DisplayName("회원 조회 중 회원을 찾지 못하면 에러 응답을 반환한다")
  void getMeReturnsNotFoundWhenMemberIsMissing() throws Exception {
    when(memberService.getMember(3L))
        .thenThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
  }

  @Test
  @DisplayName("회원 탈퇴 후 인증 쿠키를 제거한다")
  void withdrawClearsAuthenticationCookies() throws Exception {
    mockMvc.perform(delete("/api/users/me")).andExpect(status().isNoContent());

    verify(memberService).withdraw(3L);
    verify(tokenCookieManager).clearTokenCookies(any(HttpServletResponse.class));
  }

  private Member createMember(Long memberId, String email, String nickname) {
    Member member = org.mockito.Mockito.mock(Member.class);
    when(member.getId()).thenReturn(memberId);
    when(member.getEmail()).thenReturn(email);
    when(member.getNickname()).thenReturn(nickname);
    when(member.getRole()).thenReturn(MemberRole.USER);
    return member;
  }
}

package org.grit.daynomy.auth.token;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.member.domain.MemberRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenCookieManager tokenCookieManager;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String accessToken = tokenCookieManager.getAccessToken(request);

    if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticate(accessToken);
    }

    filterChain.doFilter(request, response);
  }

  private void authenticate(String accessToken) {
    try {
      Jwt jwt = jwtTokenProvider.parseAccessToken(accessToken);
      Long memberId = jwtTokenProvider.getMemberId(jwt);
      MemberRole role = jwtTokenProvider.getRole(jwt);

      AuthenticatedMember principal = new AuthenticatedMember(memberId, role);

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));

      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (InvalidTokenException exception) {
      SecurityContextHolder.clearContext();
    }
  }
}

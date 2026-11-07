package org.grit.daynomy.pmfsurvey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.exception.AuthErrorCode;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.controller.AnonymousContentVisitorCookieManager;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.pmfsurvey.exception.PmfSurveyErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PmfSurveyIdentity {

  private final AnonymousContentVisitorCookieManager anonymousVisitors;

  public boolean excluded(AuthenticatedMember member, HttpServletRequest request) {
    return "staff.daynomy.com".equalsIgnoreCase(request.getServerName())
        || (member != null && member.role() == MemberRole.ADMIN);
  }

  public boolean hasInvalidToken(HttpServletRequest request) {
    return Boolean.TRUE.equals(
        request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE));
  }

  public void requireValidToken(HttpServletRequest request) {
    if (hasInvalidToken(request)) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
  }

  public String getOrCreate(
      AuthenticatedMember member, HttpServletRequest request, HttpServletResponse response) {
    return member == null
        ? guestKey(anonymousVisitors.getOrCreate(request, response))
        : memberKey(member);
  }

  public String requireExisting(AuthenticatedMember member, HttpServletRequest request) {
    if (member != null) {
      return memberKey(member);
    }
    UUID visitorId = anonymousVisitors.get(request);
    if (visitorId == null) {
      throw new BusinessException(PmfSurveyErrorCode.PMF_SURVEY_VISITOR_REQUIRED);
    }
    return guestKey(visitorId);
  }

  public String visitSession(HttpServletRequest request) {
    String sessionId = request.getSession().getId();
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("PMF session hashing is unavailable", exception);
    }
  }

  private String memberKey(AuthenticatedMember member) {
    return "member:" + member.memberId();
  }

  private String guestKey(UUID visitorId) {
    return "guest:" + visitorId;
  }
}

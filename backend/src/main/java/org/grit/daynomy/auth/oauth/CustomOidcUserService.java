package org.grit.daynomy.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Service
public class CustomOidcUserService {

  private final MemberService memberService;
  private final OidcUserService delegate = new OidcUserService();

  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(userRequest);
    if (!StringUtils.hasText(oidcUser.getSubject()) || !StringUtils.hasText(oidcUser.getEmail())) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_google_user"), "Google 사용자 정보가 올바르지 않습니다.");
    }

    Member member =
        memberService.findOrCreateGoogleMember(
            oidcUser.getSubject(),
            oidcUser.getEmail(),
            oidcUser.getFullName(),
            oidcUser.getPicture());

    return new CustomOidcUser(member, oidcUser);
  }
}

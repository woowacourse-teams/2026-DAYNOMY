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

  private static final int MAX_NICKNAME_LENGTH = 20;

  private final MemberService memberService;
  private final OidcUserService delegate = new OidcUserService();

  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(userRequest);
    GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oidcUser.getAttributes());

    validateUserInfo(userInfo);

    Member member =
        memberService.findOrCreateGoogleMember(
            userInfo.providerId(),
            userInfo.email(),
            createNickname(userInfo.name(), userInfo.email()),
            userInfo.profileImageUrl());

    return new CustomOidcUser(member, oidcUser);
  }

  private void validateUserInfo(GoogleOAuth2UserInfo userInfo) {
    if (!StringUtils.hasText(userInfo.providerId()) || !StringUtils.hasText(userInfo.email())) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_google_user"), "Google 사용자 정보가 올바르지 않습니다.");
    }
  }

  private String createNickname(String name, String email) {
    String nickname = StringUtils.hasText(name) ? name : email.split("@")[0];

    if (nickname.length() > MAX_NICKNAME_LENGTH) {
      return nickname.substring(0, MAX_NICKNAME_LENGTH);
    }

    return nickname;
  }
}

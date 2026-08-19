package org.grit.daynomy.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private static final int MAX_NICKNAME_LENGTH = 20;

  private final MemberService memberService;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);
    GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());

    validateUserInfo(userInfo);

    Member member =
        memberService.findOrCreateGoogleMember(
            userInfo.providerId(),
            userInfo.email(),
            createNickname(userInfo.name(), userInfo.email()),
            userInfo.profileImageUrl());

    return new CustomOAuth2User(member, oAuth2User.getAttributes());
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

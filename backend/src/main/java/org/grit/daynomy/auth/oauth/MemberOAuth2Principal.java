package org.grit.daynomy.auth.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

public interface MemberOAuth2Principal extends OAuth2User {

  Long getMemberId();
}

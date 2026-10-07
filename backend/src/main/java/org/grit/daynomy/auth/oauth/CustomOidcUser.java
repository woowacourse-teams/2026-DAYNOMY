package org.grit.daynomy.auth.oauth;

import java.util.List;
import org.grit.daynomy.member.domain.Member;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class CustomOidcUser extends DefaultOidcUser {

  private final Long memberId;

  public CustomOidcUser(Member member, OidcUser delegate) {
    super(
        List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())),
        delegate.getIdToken(),
        delegate.getUserInfo());
    this.memberId = member.getId();
  }

  public Long getMemberId() {
    return memberId;
  }

  @Override
  public String getName() {
    return memberId.toString();
  }
}

package org.grit.daynomy.auth.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.member.domain.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class CustomOidcUser implements OidcUser, MemberOAuth2Principal {

  private final Long memberId;
  private final OidcUser delegate;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomOidcUser(Member member, OidcUser delegate) {
    this.memberId = member.getId();
    this.delegate = delegate;
    this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
  }

  @Override
  public Long getMemberId() {
    return memberId;
  }

  @Override
  public Map<String, Object> getClaims() {
    return delegate.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return delegate.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return delegate.getIdToken();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return delegate.getAttributes();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getName() {
    return memberId.toString();
  }
}

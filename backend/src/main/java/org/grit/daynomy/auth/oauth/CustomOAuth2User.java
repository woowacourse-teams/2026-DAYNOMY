package org.grit.daynomy.auth.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.member.domain.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class CustomOAuth2User implements OAuth2User, MemberOAuth2Principal {

  private final Long memberId;
  private final Map<String, Object> attributes;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomOAuth2User(Member member, Map<String, Object> attributes) {
    this.memberId = member.getId();
    this.attributes = attributes;
    this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
  }

  @Override
  public Long getMemberId() {
    return memberId;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
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

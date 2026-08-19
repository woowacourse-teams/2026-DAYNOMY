package org.grit.daynomy.auth.oauth;

import java.util.Map;

public class GoogleOAuth2UserInfo {

  private final Map<String, Object> attributes;

  public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  public String providerId() {
    return (String) attributes.get("sub");
  }

  public String email() {
    return (String) attributes.get("email");
  }

  public String name() {
    return (String) attributes.get("name");
  }

  public String profileImageUrl() {
    return (String) attributes.get("picture");
  }
}

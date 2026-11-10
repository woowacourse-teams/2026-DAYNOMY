package org.grit.daynomy.emailsubscription.domain;

import org.springframework.web.util.UriComponentsBuilder;

final class EmailCampaignLink {
  private EmailCampaignLink() {}

  static String to(String baseUrl, String path) {
    return UriComponentsBuilder.fromUriString(baseUrl + path)
        .replaceQueryParam("utm_source", "daynomy")
        .replaceQueryParam("utm_medium", "email")
        .replaceQueryParam("utm_campaign", "portfolio_updates")
        .build()
        .toUriString();
  }
}

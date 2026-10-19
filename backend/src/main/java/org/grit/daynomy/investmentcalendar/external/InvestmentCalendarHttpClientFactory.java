package org.grit.daynomy.investmentcalendar.external;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class InvestmentCalendarHttpClientFactory {

  private static final String USER_AGENT = "DAYNOMY/1.0 (contact: paperchoigo@gmail.com)";

  private InvestmentCalendarHttpClientFactory() {}

  static RestClient create(String baseUrl, InvestmentCalendarExternalProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .requestFactory(requestFactory)
        .build();
  }
}

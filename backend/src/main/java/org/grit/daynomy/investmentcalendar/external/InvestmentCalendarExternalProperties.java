package org.grit.daynomy.investmentcalendar.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.investment-calendar")
public record InvestmentCalendarExternalProperties(
    Duration connectTimeout,
    Duration readTimeout,
    String blsBaseUrl,
    String blsScheduleUrl,
    String bokBaseUrl,
    String ecosBaseUrl,
    String ecosApiKey,
    String dartBaseUrl,
    String dartApiKey) {

  public InvestmentCalendarExternalProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(15);
    }
  }
}

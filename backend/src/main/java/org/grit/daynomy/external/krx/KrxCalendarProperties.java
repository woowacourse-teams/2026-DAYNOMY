package org.grit.daynomy.external.krx;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.krx-calendar")
public record KrxCalendarProperties(
    String baseUrl, Duration connectTimeout, Duration readTimeout) {}

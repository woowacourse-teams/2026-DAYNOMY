package org.grit.daynomy.external.nyse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.nyse-calendar")
public record NyseCalendarProperties(
    String baseUrl, Duration connectTimeout, Duration readTimeout) {}

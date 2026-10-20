package org.grit.daynomy.exchangerate.service;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class ExchangeRateClockConfig {
  @Bean
  public Clock exchangeRateClock() {
    return Clock.systemUTC();
  }
}

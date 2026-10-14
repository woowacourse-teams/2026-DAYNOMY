package org.grit.daynomy.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidationConfig {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Bean
  public ValidationConfigurationCustomizer validationConfigurationCustomizer() {
    return configuration -> configuration.clockProvider(() -> Clock.system(SEOUL));
  }
}

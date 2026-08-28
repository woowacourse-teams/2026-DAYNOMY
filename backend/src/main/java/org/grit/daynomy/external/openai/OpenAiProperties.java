package org.grit.daynomy.external.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "external.openai")
public record OpenAiProperties(
    String apiKey,
    String baseUrl,
    String model,
    String imageModel,
    Duration connectTimeout,
    Duration readTimeout) {

  @ConstructorBinding
  public OpenAiProperties {}

  public OpenAiProperties(String apiKey, String baseUrl, String model, String imageModel) {
    this(apiKey, baseUrl, model, imageModel, Duration.ofSeconds(3), Duration.ofSeconds(120));
  }
}

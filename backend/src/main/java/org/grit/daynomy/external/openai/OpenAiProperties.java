package org.grit.daynomy.external.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "external.openai")
public record OpenAiProperties(
    String apiKey,
    String baseUrl,
    String imageModel,
    String economyNewsModel,
    Duration connectTimeout,
    Duration readTimeout) {

  @ConstructorBinding
  public OpenAiProperties {}

  public OpenAiProperties(String apiKey, String baseUrl, String imageModel) {
    this(apiKey, baseUrl, imageModel, "gpt-5.5", Duration.ofSeconds(3), Duration.ofSeconds(120));
  }
}

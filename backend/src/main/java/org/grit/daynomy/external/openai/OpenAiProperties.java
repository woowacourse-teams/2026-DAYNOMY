package org.grit.daynomy.external.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model, String imageModel) {}

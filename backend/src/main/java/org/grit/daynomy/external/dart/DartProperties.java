package org.grit.daynomy.external.dart;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.dart")
public record DartProperties(String apiKey, String baseUrl) {}

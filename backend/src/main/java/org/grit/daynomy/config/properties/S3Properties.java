package org.grit.daynomy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(String region, String bucket, String publicBaseUrl) {}

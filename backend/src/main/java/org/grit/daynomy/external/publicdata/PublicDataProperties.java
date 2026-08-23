package org.grit.daynomy.external.publicdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.public-data")
public record PublicDataProperties(String serviceKey, String stockPriceUrl) {}

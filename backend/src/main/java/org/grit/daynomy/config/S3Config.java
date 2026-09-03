package org.grit.daynomy.config;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.config.properties.S3Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@RequiredArgsConstructor
@Configuration
public class S3Config {

  private final S3Properties s3Properties;

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .region(Region.of(s3Properties.region()))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build();
  }
}

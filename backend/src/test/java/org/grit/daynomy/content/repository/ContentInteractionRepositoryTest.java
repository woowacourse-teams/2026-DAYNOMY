package org.grit.daynomy.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.grit.daynomy.content.domain.ContentInteraction;
import org.grit.daynomy.content.domain.ContentInteractionType;
import org.grit.daynomy.content.domain.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContentInteractionRepositoryTest {

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("daynomy")
          .withUsername("daynomy")
          .withPassword("daynomy");

  @DynamicPropertySource
  static void configurePostgresql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired private ContentInteractionRepository repository;

  @Test
  void aggregatesWeightedPopularityWithinRequestedPeriod() {
    LocalDate today = LocalDate.of(2026, 10, 20);
    repository.save(interaction(ContentType.NEWS, 1L, ContentInteractionType.OPEN, today));
    repository.save(interaction(ContentType.NEWS, 1L, ContentInteractionType.EXPAND, today));
    repository.save(
        interaction(ContentType.NEWS, 1L, ContentInteractionType.OUTBOUND_CLICK, today));
    repository.save(interaction(ContentType.PERSON_POST, 2L, ContentInteractionType.OPEN, today));
    repository.save(interaction(ContentType.ASSET_CONTENT, 4L, ContentInteractionType.OPEN, today));
    repository.save(
        interaction(
            ContentType.ASSET_CONTENT,
            3L,
            ContentInteractionType.OUTBOUND_CLICK,
            today.minusDays(7)));
    repository.flush();

    var popularity = repository.findPopularitySince(today.minusDays(6), PageRequest.of(0, 2));

    assertThat(popularity).hasSize(2);
    assertThat(popularity.getTotalElements()).isEqualTo(3);
    assertThat(popularity.hasNext()).isTrue();
    assertThat(popularity.getContent().getFirst().getContentType()).isEqualTo("NEWS");
    assertThat(popularity.getContent().getFirst().getContentId()).isEqualTo(1L);
    assertThat(popularity.getContent().getFirst().getScore()).isEqualTo(5L);
    assertThat(popularity.getContent().get(1).getScore()).isEqualTo(1L);

    var lastPage = repository.findPopularitySince(today.minusDays(6), PageRequest.of(1, 2));
    assertThat(lastPage).hasSize(1);
    assertThat(lastPage.hasNext()).isFalse();
  }

  @Test
  void ignoresRepeatedInteractionWithSameDeduplicationKey() {
    LocalDate today = LocalDate.of(2026, 10, 20);
    String deduplicationKey = "a".repeat(64);

    int inserted =
        repository.insertIfAbsent(
            UUID.randomUUID(),
            deduplicationKey,
            "NEWS",
            1L,
            "OPEN",
            today,
            Instant.parse("2026-10-20T03:00:00Z"));
    int ignored =
        repository.insertIfAbsent(
            UUID.randomUUID(),
            deduplicationKey,
            "NEWS",
            1L,
            "OPEN",
            today,
            Instant.parse("2026-10-20T03:01:00Z"));

    assertThat(inserted).isEqualTo(1);
    assertThat(ignored).isZero();
  }

  private ContentInteraction interaction(
      ContentType contentType,
      Long contentId,
      ContentInteractionType interactionType,
      LocalDate date) {
    return ContentInteraction.create(
        UUID.randomUUID(),
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", ""),
        contentType,
        contentId,
        interactionType,
        date);
  }
}

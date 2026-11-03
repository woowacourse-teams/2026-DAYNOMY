package org.grit.daynomy.person.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
class FeaturedPersonPostRepositoryTest {

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

  @Autowired private TestEntityManager entityManager;
  @Autowired private FeaturedPersonPostRepository postRepository;

  @Test
  void publicPageContainsOnlyApprovedPostsInPublishedOrder() {
    FeaturedPerson first = savePerson("첫 번째 인물");
    FeaturedPerson second = savePerson("두 번째 인물");
    FeaturedPersonPost latest =
        saveApproved(first, "최신 글", "latest", Instant.parse("2026-10-03T00:00:00Z"));
    FeaturedPersonPost older =
        saveApproved(second, "이전 글", "older", Instant.parse("2026-10-02T00:00:00Z"));
    FeaturedPersonPost withoutPublishedAt = saveApproved(first, "발행일 없는 글", "undated", null);
    entityManager.persist(
        FeaturedPersonPost.createPending(
            first,
            ContentSourceType.X,
            "승인 대기 글",
            "https://x.com/loadtest/status/pending",
            Instant.parse("2026-10-04T00:00:00Z")));
    entityManager.flush();

    var firstPage =
        postRepository.findPublicPageByStatus(
            FeaturedPersonPostStatus.APPROVED, PageRequest.of(0, 2));
    var secondPage =
        postRepository.findPublicPageByStatus(
            FeaturedPersonPostStatus.APPROVED, PageRequest.of(1, 2));
    var personPage =
        postRepository.findPublicPageByPersonIdAndStatus(
            first.getId(), FeaturedPersonPostStatus.APPROVED, PageRequest.of(0, 10));

    assertThat(firstPage.getContent()).containsExactly(latest, older);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(secondPage.getContent()).containsExactly(withoutPublishedAt);
    assertThat(personPage.getContent()).containsExactly(latest, withoutPublishedAt);
  }

  private FeaturedPerson savePerson(String name) {
    return entityManager.persist(FeaturedPerson.create(name, null, null, null, null));
  }

  private FeaturedPersonPost saveApproved(
      FeaturedPerson person, String title, String path, Instant publishedAt) {
    return entityManager.persist(
        FeaturedPersonPost.create(
            person,
            ContentSourceType.X,
            title,
            "https://x.com/loadtest/status/" + path,
            publishedAt));
  }
}

package org.grit.daynomy.news.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsGenerationTopicRepositoryTest {

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

  @Autowired private NewsGenerationTopicRepository topicRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("동시 예약은 잠긴 주제를 건너뛰고 서로 다른 주제를 선택한다")
  void concurrentReservationSkipsLockedTopic() throws Exception {
    topicRepository.saveAllAndFlush(List.of(topic("a", 1), topic("b", 2)));
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  transaction.execute(
                      status -> {
                        NewsGenerationTopic selected =
                            topicRepository
                                .findReadyForUpdate("KOREA", LocalDate.of(2026, 11, 4), 1)
                                .getFirst();
                        firstLocked.countDown();
                        await(releaseFirst);
                        selected.reserve();
                        return selected.getId();
                      }));
      assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

      var second =
          executor.submit(
              () ->
                  transaction.execute(
                      status -> {
                        NewsGenerationTopic selected =
                            topicRepository
                                .findReadyForUpdate("KOREA", LocalDate.of(2026, 11, 4), 1)
                                .getFirst();
                        selected.reserve();
                        return selected.getId();
                      }));

      Long secondId = second.get(5, TimeUnit.SECONDS);
      releaseFirst.countDown();
      Long firstId = first.get(5, TimeUnit.SECONDS);

      assertThat(secondId).isNotEqualTo(firstId);
    } finally {
      releaseFirst.countDown();
      topicRepository.deleteAll();
    }
  }

  private NewsGenerationTopic topic(String keyCharacter, int sourceNumber) {
    return NewsGenerationTopic.create(
        keyCharacter.repeat(64),
        NewsGenerationType.KOREA,
        "경제 뉴스 " + keyCharacter,
        LocalDate.of(2026, 11, 6),
        Category.STOCK,
        List.of("사실 1", "사실 2", "사실 3"),
        List.of(
            new NewsSourceInfo("출처 1", "https://example.com/" + sourceNumber + "/1"),
            new NewsSourceInfo("출처 2", "https://example.com/" + sourceNumber + "/2")));
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out while waiting for concurrent reservation.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Concurrent reservation was interrupted.", exception);
    }
  }
}

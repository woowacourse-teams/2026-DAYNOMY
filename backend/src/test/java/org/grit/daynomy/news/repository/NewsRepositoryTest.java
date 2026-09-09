package org.grit.daynomy.news.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.domain.NewsStatus;
import org.junit.jupiter.api.DisplayName;
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
class NewsRepositoryTest {

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

  @Autowired private NewsRepository newsRepository;

  @Test
  @DisplayName("관리자 뉴스 목록 조회는 필터 조합별로 동작한다")
  void findAdminNewsFiltersByStatusAndCategory() {
    entityManager.persist(
        News.createDraft(
            "초안 주식 뉴스",
            "본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/draft-stock")),
            Category.STOCK));
    entityManager.persist(
        News.createDraft(
            "초안 부동산 뉴스",
            "본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/draft-estate")),
            Category.REAL_ESTATE));
    entityManager.persist(
        News.createPublished(
            "발행 주식 뉴스",
            "본문",
            null,
            List.of(new NewsSourceInfo("DART", "https://example.com/published-stock")),
            Category.STOCK,
            java.time.Instant.parse("2026-08-17T10:00:00Z")));
    entityManager.flush();

    var pageable = PageRequest.of(0, 10);
    var allNews = newsRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    var drafts = newsRepository.findByStatusOrderByCreatedAtDescIdDesc(NewsStatus.DRAFT, pageable);
    var stockNews =
        newsRepository.findByCategoryOrderByCreatedAtDescIdDesc(Category.STOCK, pageable);
    var draftStockNews =
        newsRepository.findByStatusAndCategoryOrderByCreatedAtDescIdDesc(
            NewsStatus.DRAFT, Category.STOCK, pageable);

    assertThat(allNews.getContent())
        .extracting(News::getTitle)
        .containsExactlyInAnyOrder("발행 주식 뉴스", "초안 부동산 뉴스", "초안 주식 뉴스");
    assertThat(drafts.getContent())
        .extracting(News::getTitle)
        .containsExactlyInAnyOrder("초안 주식 뉴스", "초안 부동산 뉴스");
    assertThat(stockNews.getContent())
        .extracting(News::getTitle)
        .containsExactlyInAnyOrder("초안 주식 뉴스", "발행 주식 뉴스");
    assertThat(draftStockNews.getContent()).extracting(News::getTitle).containsExactly("초안 주식 뉴스");
  }
}

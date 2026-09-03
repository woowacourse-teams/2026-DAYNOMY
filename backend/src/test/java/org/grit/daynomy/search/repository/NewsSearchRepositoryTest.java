package org.grit.daynomy.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.domain.NewsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class NewsSearchRepositoryTest {

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

  @Autowired private NewsSearchRepository newsSearchRepository;

  @Test
  @DisplayName("뉴스 검색 쿼리는 제목·설명·본문을 검색하고 카테고리·정렬·페이징을 적용한다")
  void searchNewsByKeywordAndCategory() {
    entityManager.persist(
        createNews(
            "금리 제목 뉴스",
            "일반 본문",
            "일반 설명",
            "title-match",
            Category.BOND,
            Instant.parse("2026-08-14T10:00:00Z")));
    entityManager.persist(
        createNews(
            "설명 일치 뉴스",
            "일반 본문",
            "금리 설명",
            "description-match",
            Category.BOND,
            Instant.parse("2026-08-14T12:00:00Z")));
    entityManager.persist(
        createNews(
            "본문 일치 뉴스",
            "금리 본문",
            "일반 설명",
            "content-match",
            Category.STOCK,
            Instant.parse("2026-08-14T11:00:00Z")));
    entityManager.persist(
        News.createDraft(
            "금리 초안 뉴스",
            "금리 본문",
            "금리 설명",
            "image.png",
            NewsSource.DART,
            "draft-match",
            "https://example.com/draft-match",
            Category.BOND));
    entityManager.flush();

    Sort latestFirst = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
    var allResults =
        newsSearchRepository.search(
            "금리", null, NewsStatus.PUBLISHED, PageRequest.of(0, 10, latestFirst));
    var bondPage =
        newsSearchRepository.search(
            "금리", Category.BOND, NewsStatus.PUBLISHED, PageRequest.of(0, 1, latestFirst));

    assertThat(allResults.getContent())
        .extracting(News::getTitle)
        .containsExactly("설명 일치 뉴스", "본문 일치 뉴스", "금리 제목 뉴스");
    assertThat(bondPage.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("설명 일치 뉴스");
    assertThat(bondPage.getTotalElements()).isEqualTo(2);
    assertThat(bondPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("뉴스 검색 쿼리는 LIKE 와일드카드를 일반 문자로 검색한다")
  void searchNewsTreatsLikeWildcardsAsLiteralCharacters() {
    entityManager.persist(
        createNews(
            "금% 문자 뉴스",
            "퍼센트 문자 검색",
            null,
            "percent-match",
            Category.GOLD,
            Instant.parse("2026-08-14T10:00:00Z")));
    entityManager.persist(
        createNews(
            "금_ 문자 뉴스",
            "밑줄 문자 검색",
            null,
            "underscore-match",
            Category.GOLD,
            Instant.parse("2026-08-15T10:00:00Z")));
    entityManager.persist(
        createNews(
            "금리 일반 뉴스",
            "일반 검색",
            null,
            "normal-news",
            Category.BOND,
            Instant.parse("2026-08-16T10:00:00Z")));
    entityManager.flush();

    PageRequest pageable = PageRequest.of(0, 20);
    var percentResults = newsSearchRepository.search("금!%", null, NewsStatus.PUBLISHED, pageable);
    var underscoreResults =
        newsSearchRepository.search("금!_", null, NewsStatus.PUBLISHED, pageable);

    assertThat(percentResults.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("금% 문자 뉴스");
    assertThat(underscoreResults.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("금_ 문자 뉴스");
  }

  private News createNews(
      String title,
      String content,
      String description,
      String externalId,
      Category category,
      Instant publishedAt) {
    return News.createPublished(
        title,
        content,
        description,
        "image.png",
        NewsSource.DART,
        externalId,
        "https://example.com/" + externalId,
        category,
        publishedAt);
  }
}

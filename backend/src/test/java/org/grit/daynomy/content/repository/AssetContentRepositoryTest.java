package org.grit.daynomy.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
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
class AssetContentRepositoryTest {
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
  @Autowired private AssetContentRepository repository;

  @Test
  void listsDistinctAssetsWithExternalContentOrPublishedNews() {
    Asset external = entityManager.persist(new Asset("외부 자료", AssetCategory.STOCK, "TEST-1"));
    Asset published = entityManager.persist(new Asset("발행 기사", AssetCategory.STOCK, "TEST-2"));
    Asset draft = entityManager.persist(new Asset("초안", AssetCategory.STOCK, "TEST-3"));
    Asset deleted = entityManager.persist(new Asset("삭제", AssetCategory.STOCK, "TEST-4"));
    entityManager.persist(new Asset("자료 없음", AssetCategory.STOCK, "TEST-5"));
    News publishedNews =
        entityManager.persist(
            News.createPublished("발행", "본문", null, List.of(), Category.STOCK, Instant.now()));
    News draftNews =
        entityManager.persist(News.createDraft("초안", "본문", null, List.of(), Category.STOCK));
    News deletedNews =
        entityManager.persist(
            News.createPublished("삭제", "본문", null, List.of(), Category.STOCK, Instant.now()));
    deletedNews.delete();
    entityManager.flush();

    entityManager.persist(
        AssetContent.create(external, ContentSourceType.YOUTUBE, "영상", "https://example.com/1"));
    entityManager.persist(
        AssetContent.create(external, ContentSourceType.X, "게시물", "https://example.com/2"));
    entityManager.persist(AssetContent.createInternalNews(published, publishedNews));
    entityManager.persist(AssetContent.createInternalNews(draft, draftNews));
    entityManager.persist(AssetContent.createInternalNews(deleted, deletedNews));
    entityManager.flush();

    var first = repository.findDiscoverableAssetIds(NewsStatus.PUBLISHED, PageRequest.of(0, 1));
    var second = repository.findDiscoverableAssetIds(NewsStatus.PUBLISHED, PageRequest.of(1, 1));

    assertThat(first.getContent()).containsExactly(external.getId());
    assertThat(first.hasNext()).isTrue();
    assertThat(second.getContent()).containsExactly(published.getId());
    assertThat(second.hasNext()).isFalse();
  }
}

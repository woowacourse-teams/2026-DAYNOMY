package org.grit.daynomy.news.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.Industry;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.IndustryRepository;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.content.service.AssetContentService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsFeedAcceptanceTest {

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

  @LocalServerPort private int port;
  @Autowired private NewsRepository newsRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private IndustryRepository industryRepository;
  @Autowired private AssetContentRepository assetContentRepository;
  @Autowired private AssetContentService assetContentService;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-21T01:00:00Z"));
    assetContentRepository.deleteAll();
    newsRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @Test
  void pagesLatestNewsFeed() {
    News oldest = published("첫 번째 소식", "2026-10-18T00:00:00Z");
    News middle = published("두 번째 소식", "2026-10-19T00:00:00Z");
    News latest = published("세 번째 소식", "2026-10-20T00:00:00Z");

    given()
        .port(port)
        .queryParam("size", 2)
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items.id", equalTo(List.of(latest.getId().intValue(), middle.getId().intValue())))
        .body("totalElements", equalTo(3))
        .body("totalPages", equalTo(2));

    given()
        .port(port)
        .queryParam("page", 2)
        .queryParam("size", 2)
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(oldest.getId().intValue()));
  }

  @Test
  void filtersNewsByRelatedAssetIndustry() {
    Industry semiconductor =
        industryRepository.findByCodeAndActiveTrue("SEMICONDUCTOR").orElseThrow();
    Asset asset =
        Asset.listedStock("반도체 종목", "999991", StockMarket.KOSPI, null, LocalDate.of(2026, 10, 20));
    asset.addIndustry(semiconductor);
    asset = assetRepository.save(asset);
    News semiconductorNews = published("반도체 소식", "2026-10-20T00:00:00Z");
    published("일반 소식", "2026-10-19T00:00:00Z");
    assetContentService.syncNewsContents(semiconductorNews, List.of(asset.getId()));

    given()
        .port(port)
        .queryParam("industry", "SEMICONDUCTOR")
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(semiconductorNews.getId().intValue()))
        .body("totalElements", equalTo(1));
  }

  @Test
  void excludesNonSecurityAssetsFromIndustryFeed() {
    Industry semiconductor =
        industryRepository.findByCodeAndActiveTrue("SEMICONDUCTOR").orElseThrow();
    Asset gold = new Asset("금", AssetCategory.GOLD, "GOLD-INDUSTRY");
    gold.addIndustry(semiconductor);
    gold = assetRepository.save(gold);
    News news = published("금 소식", "2026-10-20T00:00:00Z");
    assetContentService.syncNewsContents(news, List.of(gold.getId()));
    assetContentService.createContent(
        gold.getId(),
        new AssetContentRequest(
            ContentSourceType.X,
            "금 외부 소식",
            "https://x.com/example/status/gold",
            null,
            LocalDate.of(2026, 10, 20)));

    given()
        .port(port)
        .queryParam("industry", "SEMICONDUCTOR")
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items", hasSize(0))
        .body("totalElements", equalTo(0));
  }

  @Test
  void rejectsUnknownIndustry() {
    given()
        .port(port)
        .queryParam("industry", "UNKNOWN")
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(400)
        .body("code", equalTo("INDUSTRY_NOT_FOUND"));
  }

  @Test
  void filtersDuplicateExternalContentBeforeUrlDeduplication() {
    Industry semiconductor =
        industryRepository.findByCodeAndActiveTrue("SEMICONDUCTOR").orElseThrow();
    Industry automobile = industryRepository.findByCodeAndActiveTrue("AUTOMOBILE").orElseThrow();
    Asset semiconductorStock =
        Asset.listedStock("반도체 종목", "999992", StockMarket.KOSPI, null, LocalDate.of(2026, 10, 20));
    semiconductorStock.addIndustry(semiconductor);
    semiconductorStock = assetRepository.save(semiconductorStock);
    Asset automobileStock =
        Asset.listedStock("자동차 종목", "999993", StockMarket.KOSPI, null, LocalDate.of(2026, 10, 20));
    automobileStock.addIndustry(automobile);
    automobileStock = assetRepository.save(automobileStock);
    AssetContentRequest request =
        new AssetContentRequest(
            ContentSourceType.X,
            "공통 URL 콘텐츠",
            "https://x.com/example/status/1",
            null,
            LocalDate.of(2026, 10, 20));
    assetContentService.createContent(semiconductorStock.getId(), request);
    assetContentService.createContent(automobileStock.getId(), request);

    given()
        .port(port)
        .queryParam("industry", "SEMICONDUCTOR")
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].kind", equalTo("ASSET_CONTENT"))
        .body("items[0].assetId", equalTo(semiconductorStock.getId().intValue()));
  }

  private News published(String title, String publishedAt) {
    return newsRepository.save(
        News.createPublished(
            title, "본문", null, List.of(), Category.STOCK, Instant.parse(publishedAt)));
  }
}

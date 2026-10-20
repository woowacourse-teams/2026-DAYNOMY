package org.grit.daynomy.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
class StockDailyPriceRepositoryTest {

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
  @Autowired private StockDailyPriceRepository repository;

  @Test
  @DisplayName("요청한 종목마다 서로 다른 거래일의 최근 가격 두 개만 최신순으로 조회한다")
  void findsLatestTwoPricesPerRequestedAsset() {
    Asset first = asset("FIRST");
    Asset second = asset("SECOND");
    Asset excluded = asset("EXCLUDED");
    price(first, 1);
    var firstLatest = price(first, 8);
    var firstPrevious = price(first, 4);
    var secondPrevious = price(second, 2);
    price(second, 1);
    var secondLatest = price(second, 6);
    price(excluded, 9);
    entityManager.flush();
    entityManager.clear();

    var result = repository.findLatestTwoByAssetIds(List.of(second.getId(), first.getId()));

    assertThat(result)
        .extracting(StockDailyPrice::getId)
        .containsExactly(
            firstLatest.getId(),
            firstPrevious.getId(),
            secondLatest.getId(),
            secondPrevious.getId());
    assertThat(result)
        .extracting(StockDailyPrice::getClosePrice)
        .containsExactly(
            new BigDecimal("108.0000"),
            new BigDecimal("104.0000"),
            new BigDecimal("106.0000"),
            new BigDecimal("102.0000"));
  }

  @Test
  @DisplayName("가격이 하나인 종목은 하나만 반환하고 가격 없는 종목은 제외한다")
  void handlesMissingAndSinglePrice() {
    Asset single = asset("SINGLE");
    Asset missing = asset("MISSING");
    var onlyPrice = price(single, 3);
    entityManager.flush();
    entityManager.clear();

    var result = repository.findLatestTwoByAssetIds(List.of(single.getId(), missing.getId()));

    assertThat(result).extracting(StockDailyPrice::getId).containsExactly(onlyPrice.getId());
    assertThat(repository.findLatestTwoByAssetIds(List.of(missing.getId()))).isEmpty();
  }

  @Test
  @DisplayName("조회할 종목이 없으면 빈 결과를 반환한다")
  void handlesEmptyAssets() {
    assertThat(repository.findLatestTwoByAssetIds(List.of())).isEmpty();
  }

  private Asset asset(String code) {
    return entityManager.persist(new Asset(code, AssetCategory.STOCK, code));
  }

  private StockDailyPrice price(Asset asset, int day) {
    return entityManager.persist(
        new StockDailyPrice(asset, LocalDate.of(2026, 10, day), BigDecimal.valueOf(100 + day)));
  }
}

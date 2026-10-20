package org.grit.daynomy.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
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
class AssetRepositoryTest {

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
  @Autowired private AssetRepository assetRepository;

  @Test
  @DisplayName("국내 주식·ETF 검색은 상장 종목의 종목명과 종목코드만 검색한다")
  void searchListedStocksByNameAndCode() {
    LocalDate baseDate = LocalDate.of(2026, 9, 18);
    Asset samsung =
        Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, "KR7005930003", baseDate);
    Asset samsungPreferred =
        Asset.listedStock("삼성전자우", "005935", StockMarket.KOSPI, "KR7005931001", baseDate);
    Asset delisted =
        Asset.listedStock("삼성구주", "005931", StockMarket.KOSPI, "KR7005932009", baseDate);
    Asset samsungEtf =
        Asset.listedSecurity(
            "KODEX 삼성그룹", AssetCategory.ETF, "102780", StockMarket.KOSPI, "KR7102780002", baseDate);
    delisted.delist();
    entityManager.persist(samsung);
    entityManager.persist(samsungPreferred);
    entityManager.persist(delisted);
    entityManager.persist(samsungEtf);
    entityManager.flush();

    var nameResults =
        assetRepository.searchListedSecurities(
            "삼성",
            Set.of(AssetCategory.STOCK, AssetCategory.ETF),
            StockMarket.domesticMarkets(),
            PageRequest.of(0, 20));
    var codeResults =
        assetRepository.searchListedSecurities(
            "005930",
            Set.of(AssetCategory.STOCK, AssetCategory.ETF),
            StockMarket.domesticMarkets(),
            PageRequest.of(0, 20));

    assertThat(nameResults)
        .extracting(Asset::getAssetCode)
        .containsExactly("005930", "005935", "102780");
    assertThat(codeResults).singleElement().isSameAs(samsung);
  }

  @Test
  void searchKeepsCountriesSeparate() {
    LocalDate date = LocalDate.of(2026, 10, 16);
    entityManager.persist(
        Asset.listedStock("Apple Korea", "123456", StockMarket.KOSPI, null, date));
    entityManager.persist(Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, date));
    entityManager.persist(
        Asset.listedSecurity(
            "Apple ETF", AssetCategory.ETF, "AAPF.US", StockMarket.BATS, null, date));
    entityManager.flush();
    var categories = Set.of(AssetCategory.STOCK, AssetCategory.ETF);
    assertThat(
            assetRepository.searchListedSecurities(
                "Apple", categories, StockMarket.domesticMarkets(), PageRequest.of(0, 20)))
        .extracting(Asset::getAssetCode)
        .containsExactly("123456");
    assertThat(
            assetRepository.searchListedSecurities(
                "Apple", categories, StockMarket.usMarkets(), PageRequest.of(0, 20)))
        .extracting(Asset::getAssetCode)
        .containsExactly("AAPL.US", "AAPF.US");
  }

  @Test
  void usTickerRowsCanShareProviderIsin() {
    LocalDate date = LocalDate.of(2026, 10, 16);
    String isin = "US0000000001";
    entityManager.persist(
        Asset.listedSecurity(
            "Old ticker", AssetCategory.ETF, "OLD.US", StockMarket.NASDAQ, isin, date));
    entityManager.persist(
        Asset.listedSecurity(
            "New ticker", AssetCategory.ETF, "NEW.US", StockMarket.NASDAQ, isin, date));
    entityManager.flush();
    entityManager.clear();
    assertThat(assetRepository.findByCategoryAndAssetCode(AssetCategory.ETF, "OLD.US")).isPresent();
    assertThat(assetRepository.findByCategoryAndAssetCode(AssetCategory.ETF, "NEW.US")).isPresent();
  }

  @Test
  void domesticIsinRemainsUnique() {
    LocalDate date = LocalDate.of(2026, 10, 16);
    entityManager.persistAndFlush(
        Asset.listedStock("First", "123450", StockMarket.KOSPI, "KR0000000001", date));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> {
              entityManager.persistAndFlush(
                  Asset.listedStock("Second", "123451", StockMarket.KOSDAQ, "KR0000000001", date));
            })
        .isInstanceOf(jakarta.persistence.PersistenceException.class);
  }
}

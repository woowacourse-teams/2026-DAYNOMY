package org.grit.daynomy.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
  @DisplayName("국내 주식 검색은 상장 종목의 종목명과 종목코드만 검색한다")
  void searchListedStocksByNameAndCode() {
    LocalDate baseDate = LocalDate.of(2026, 9, 18);
    Asset samsung =
        Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, "KR7005930003", baseDate);
    Asset samsungPreferred =
        Asset.listedStock("삼성전자우", "005935", StockMarket.KOSPI, "KR7005931001", baseDate);
    Asset delisted =
        Asset.listedStock("삼성구주", "005931", StockMarket.KOSPI, "KR7005932009", baseDate);
    delisted.delist();
    entityManager.persist(samsung);
    entityManager.persist(samsungPreferred);
    entityManager.persist(delisted);
    entityManager.flush();

    var nameResults =
        assetRepository.searchListedStocks("삼성", AssetCategory.STOCK, PageRequest.of(0, 20));
    var codeResults =
        assetRepository.searchListedStocks("005930", AssetCategory.STOCK, PageRequest.of(0, 20));

    assertThat(nameResults).extracting(Asset::getAssetCode).containsExactly("005930", "005935");
    assertThat(codeResults).singleElement().isSameAs(samsung);
  }
}

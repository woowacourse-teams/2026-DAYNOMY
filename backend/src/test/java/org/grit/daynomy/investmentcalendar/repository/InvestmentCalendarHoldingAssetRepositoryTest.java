package org.grit.daynomy.investmentcalendar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
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
class InvestmentCalendarHoldingAssetRepositoryTest {

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
  @Autowired private InvestmentCalendarHoldingAssetRepository repository;

  @Test
  void findsDistinctListedStocksAndEtfsHeldInPortfolios() {
    LocalDate baseDate = LocalDate.of(2026, 10, 7);
    Asset stock = Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, "KR7005930003", baseDate);
    Asset etf =
        Asset.listedSecurity(
            "KODEX 200", AssetCategory.ETF, "069500", StockMarket.KOSPI, "KR7069500007", baseDate);
    Asset delisted =
        Asset.listedStock("상장폐지주", "000001", StockMarket.KOSPI, "KR7000000000", baseDate);
    delisted.delist();
    Asset crypto = new Asset("비트코인", AssetCategory.VIRTUAL_ASSET, "BTC");
    entityManager.persist(stock);
    entityManager.persist(etf);
    entityManager.persist(delisted);
    entityManager.persist(crypto);

    Portfolio firstPortfolio = portfolio("first", "첫회원");
    Portfolio secondPortfolio = portfolio("second", "둘회원");
    entityManager.persist(new PortfolioHolding(firstPortfolio, stock, 1L, BigDecimal.ONE));
    entityManager.persist(new PortfolioHolding(firstPortfolio, etf, 1L, BigDecimal.ONE));
    entityManager.persist(new PortfolioHolding(firstPortfolio, delisted, 1L, BigDecimal.ONE));
    entityManager.persist(new PortfolioHolding(firstPortfolio, crypto, 1L, BigDecimal.ONE));
    entityManager.persist(new PortfolioHolding(secondPortfolio, stock, 1L, BigDecimal.ONE));
    entityManager.flush();

    var assets =
        repository.findDistinctListedAssetsByCategories(
            Set.of(AssetCategory.STOCK, AssetCategory.ETF));

    assertThat(assets)
        .extracting(Asset::getAssetCode)
        .containsExactlyInAnyOrder("005930", "069500");
  }

  private Portfolio portfolio(String providerId, String nickname) {
    Member member =
        Member.createGoogleMember(providerId, providerId + "@example.com", nickname, null);
    entityManager.persist(member);
    Portfolio portfolio = Portfolio.create(member);
    entityManager.persist(portfolio);
    return portfolio;
  }
}

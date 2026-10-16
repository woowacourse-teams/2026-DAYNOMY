package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.hibernate.Hibernate;
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
class PortfolioHoldingHistoryV2RepositoryTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired private TestEntityManager em;
  @Autowired private PortfolioHoldingHistoryRepository histories;

  @Test
  void fetchesAssetsOrdersDateAndIdAndIsolatesPortfolios() {
    Portfolio owner = portfolio();
    Portfolio other = portfolio();
    String key = UUID.randomUUID().toString();
    Asset asset =
        em.persist(
            Asset.listedStock(
                "V2 조회 종목",
                key,
                StockMarket.KOSPI,
                "KR" + key.replace("-", "").substring(0, 10),
                LocalDate.of(2026, 9, 1)));
    var later = history(owner, asset, LocalDate.of(2026, 9, 2));
    var first = history(owner, asset, LocalDate.of(2026, 9, 1));
    var tie = history(owner, asset, LocalDate.of(2026, 9, 1));
    history(other, asset, LocalDate.of(2026, 9, 1));
    em.flush();
    em.getEntityManager()
        .createNativeQuery(
            "update portfolio_holding_histories set created_at = '2026-09-01T00:00:00Z'")
        .executeUpdate();
    Long portfolioId = owner.getId();
    List<Long> expected = List.of(first.getId(), tie.getId(), later.getId());
    em.clear();
    var result = histories.findOrderedWithAssetsByPortfolioId(portfolioId);
    assertThat(result)
        .extracting(PortfolioHoldingHistory::getId)
        .containsExactlyElementsOf(expected);
    assertThat(result)
        .allSatisfy(row -> assertThat(Hibernate.isInitialized(row.getAsset())).isTrue());
  }

  private Portfolio portfolio() {
    String key = UUID.randomUUID().toString();
    return em.persist(
        Portfolio.create(
            em.persist(
                Member.createGoogleMember(
                    key, key + "@example.invalid", key.substring(0, 12), null))));
  }

  private PortfolioHoldingHistory history(Portfolio portfolio, Asset asset, LocalDate day) {
    return em.persist(
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.OPENING_BALANCE,
            2,
            new BigDecimal("100"),
            day,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }
}

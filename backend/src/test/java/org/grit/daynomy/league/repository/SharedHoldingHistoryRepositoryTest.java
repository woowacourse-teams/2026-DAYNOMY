package org.grit.daynomy.league.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.service.*;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedHoldingHistoryRepositoryTest {
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

  @Autowired TestEntityManager entityManager;
  @Autowired SharedHoldingHistoryRepository histories;
  @Autowired PortfolioRepository originals;
  @Autowired PortfolioDailySnapshotRepository snapshots;
  @Autowired PortfolioHoldingHistoryRepository originalHistories;
  @Autowired SharedPortfolioRepository portfolios;
  @Autowired SharedHoldingRepository holdings;
  @Autowired MarketTradingDayRepository calendar;
  @Autowired StockDailyPriceRepository prices;
  @Autowired PortfolioDailyReturnRepository returns;
  @Autowired InvestorProfileRepository profiles;
  @Autowired PlatformTransactionManager transactions;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void dashboardCopiesCalculateThisWeekAndRemainStableAfterOriginalEdits(boolean mondayMissing) {
    var zone = ZoneId.of("Asia/Seoul");
    LocalDate monday = LocalDate.of(2026, 9, 21);
    var member =
        entityManager.persist(
            Member.createGoogleMember("weekly", "weekly@example.invalid", "주간이력", null));
    var asset =
        entityManager.persist(
            Asset.listedStock(
                "종목", "987654", StockMarket.KOSPI, "KR7987654000", monday.minusDays(7)));
    var original = entityManager.persist(Portfolio.create(member));
    var shared = entityManager.persist(SharedPortfolio.create(member));
    var holding =
        entityManager.persist(new SharedHolding(shared, asset, 2, BigDecimal.valueOf(100)));
    entityManager.persist(new SharedHoldingHistory(holding, SharedHoldingHistory.ChangeType.ADDED));
    var source =
        entityManager.persistAndFlush(
            new PortfolioHoldingHistory(
                original,
                asset,
                PortfolioHoldingChangeType.ADDED,
                2,
                BigDecimal.valueOf(100),
                monday,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "update portfolio_holding_histories set created_at=:at, updated_at=:at where id=:id")
        .setParameter("at", monday.plusDays(1).atTime(10, 0).atZone(zone).toInstant())
        .setParameter("id", source.getId())
        .executeUpdate();
    entityManager.refresh(source);
    var snapshot = new PortfolioDailySnapshot(original, monday);
    snapshot.update(
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(200),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
    if (!mondayMissing) entityManager.persist(snapshot);
    var tuesdaySnapshot = new PortfolioDailySnapshot(original, monday.plusDays(1));
    tuesdaySnapshot.update(
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(220),
        BigDecimal.valueOf(20),
        BigDecimal.TEN,
        BigDecimal.valueOf(20),
        BigDecimal.TEN);
    entityManager.persist(tuesdaySnapshot);
    var profile =
        InvestorProfile.create(
            member,
            "주간이력",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true, true, true, true, monday.plusDays(2).atTime(10, 0).atZone(zone).toInstant());
    entityManager.persistAndFlush(profile);
    for (int i = 0; i < 7; i++) {
      var day = calendar.findById(monday.plusDays(i)).orElseThrow();
      day.reviseTrading(i < 5);
      if (i < 2) day.collected(day.closesAt().plusSeconds(86400));
      else org.springframework.test.util.ReflectionTestUtils.setField(day, "collectedAt", null);
    }
    entityManager.persist(new StockDailyPrice(asset, monday, BigDecimal.valueOf(100)));
    entityManager.persist(new StockDailyPrice(asset, monday.plusDays(1), BigDecimal.valueOf(110)));
    entityManager.flush();
    var enrollment =
        new LeagueEnrollmentService(
            originals,
            originalHistories,
            portfolios,
            holdings,
            histories,
            calendar,
            new PortfolioLedgerCalculator());
    enrollment.includeCurrentWeek(profile);
    entityManager.flush();
    var calculation =
        new LeagueReturnService(
            profiles,
            portfolios,
            histories,
            prices,
            returns,
            calendar,
            snapshots,
            transactions,
            mock(LeagueCalculationMetrics.class),
            mock(LeagueService.class),
            mock(LeagueRankingCache.class));
    calculation.recalculate();
    entityManager.flush();
    var first =
        returns.findLatestByPortfolioIdAndBaseDateBetween(
            shared.getId(), monday, monday.plusDays(1));
    if (mondayMissing) {
      assertThat(first.getFirst().getIneligibleReason()).isEqualTo("SNAPSHOT_PENDING");
      assertThat(first.getLast().getDailyReturnRate()).isEqualByComparingTo("10");
      entityManager.persistAndFlush(snapshot);
      calculation.recalculatePending();
      entityManager.flush();
      first =
          returns.findLatestByPortfolioIdAndBaseDateBetween(
              shared.getId(), monday, monday.plusDays(1));
    }
    assertThat(first)
        .extracting(v -> v.getDailyReturnRate())
        .containsExactly(new BigDecimal("0.0000"), new BigDecimal("10.0000"));
    assertThat(profile.getTrackingStartedOn()).isEqualTo(monday);

    entityManager
        .getEntityManager()
        .createNativeQuery("update portfolio_holding_histories set quantity=100 where id=:id")
        .setParameter("id", source.getId())
        .executeUpdate();
    entityManager.clear();
    calculation.recalculate();
    entityManager.flush();
    assertThat(
            returns.findLatestByPortfolioIdAndBaseDateBetween(
                shared.getId(), monday, monday.plusDays(1)))
        .extracting(v -> v.getDailyReturnRate())
        .containsExactly(new BigDecimal("0.0000"), new BigDecimal("10.0000"));
  }

  @Test
  void copiesUseEffectiveTimeWhileLaterSharedChangesKeepTheirActualTime() {
    var member =
        entityManager.persist(
            Member.createGoogleMember("history", "history@example.invalid", "이력", null));
    var portfolio = entityManager.persist(SharedPortfolio.create(member));
    var asset = entityManager.persist(new Asset("종목", AssetCategory.STOCK, "TEST"));
    var current =
        entityManager.persist(
            new SharedHoldingHistory(
                new SharedHolding(portfolio, asset, 10, BigDecimal.TEN),
                SharedHoldingHistory.ChangeType.ADDED));
    Instant monday = Instant.parse("2026-01-05T06:30:00Z");
    var first =
        entityManager.persist(
            SharedHoldingHistory.snapshot(portfolio, asset, 2, BigDecimal.TEN, monday));
    var removal =
        entityManager.persist(
            SharedHoldingHistory.snapshot(
                portfolio, asset, 0, BigDecimal.ZERO, monday.plusSeconds(86400)));
    entityManager.flush();
    entityManager.clear();

    assertThat(histories.findEffectiveBefore(portfolio.getId(), monday.plusMillis(1)))
        .extracting(SharedHoldingHistory::getId)
        .containsExactly(first.getId());
    assertThat(
            SharedHoldingHistory.reconstructHoldings(
                histories.findEffectiveBefore(portfolio.getId(), monday.plusSeconds(86401))))
        .isEmpty();
    var all = histories.findEffectiveBefore(portfolio.getId(), Instant.now().plusSeconds(1));
    assertThat(all)
        .extracting(SharedHoldingHistory::getId)
        .containsExactly(first.getId(), removal.getId(), current.getId());
    assertThat(SharedHoldingHistory.reconstructHoldings(all))
        .extracting(SharedHoldingHistory::getQuantity)
        .containsExactly(10L);
  }
}

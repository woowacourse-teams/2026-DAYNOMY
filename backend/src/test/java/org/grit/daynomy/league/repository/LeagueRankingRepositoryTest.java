package org.grit.daynomy.league.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.HoldingPeriod;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.member.domain.Member;
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
class LeagueRankingRepositoryTest {
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

  @Autowired private TestEntityManager entityManager;
  @Autowired private PortfolioDailyReturnRepository returns;
  @Autowired private InvestorProfileRepository profiles;
  @Autowired private InvestmentDecisionRepository decisions;

  @Test
  void
      batchReturnsKeepLatestVersionIncludingIneligibleAndExcludeNonTradingDatesAndOtherPortfolios() {
    var first = portfolio("first");
    var second = portfolio("second");
    var other = portfolio("other");
    LocalDate monday = LocalDate.of(2026, 10, 19);
    dailyReturn(first, monday.minusDays(3), 99, true);
    dailyReturn(first, monday, 1, true);
    dailyReturn(first, monday, 2, false);
    dailyReturn(second, monday.plusDays(1), 1, true);
    dailyReturn(second, monday.plusDays(1), 2, false);
    var secondReturn = dailyReturn(second, monday.plusDays(1), 3, true);
    secondReturn.completeBaseline(BigDecimal.TEN, new BigDecimal("75"), Instant.now());
    var positive = dailyReturn(first, monday.plusDays(2), 1, true);
    positive.complete(
        new BigDecimal("12.5"),
        BigDecimal.TEN,
        new BigDecimal("11.25"),
        new BigDecimal("40"),
        new BigDecimal("60"),
        Instant.now());
    dailyReturn(first, monday.plusDays(5), 1, true);
    dailyReturn(other, monday, 1, true);
    entityManager.flush();
    entityManager.clear();

    assertThat(
            returns.findLatestByMemberIdInAndBaseDateBetween(
                List.of(first.getMember().getId(), second.getMember().getId()),
                monday,
                monday.plusDays(6)))
        .extracting(
            RankingDailyReturn::getMemberId,
            RankingDailyReturn::getBaseDate,
            RankingDailyReturn::isEligible,
            RankingDailyReturn::isTrackingBaseline,
            RankingDailyReturn::getDailyReturnRate,
            RankingDailyReturn::getMaxHoldingWeight,
            RankingDailyReturn::getCurrentHoldingWeight)
        .containsExactly(
            tuple(first.getMember().getId(), monday, false, false, null, null, null),
            tuple(
                second.getMember().getId(),
                monday.plusDays(1),
                true,
                true,
                new BigDecimal("0.0000"),
                new BigDecimal("75.00"),
                new BigDecimal("75.00")),
            tuple(
                first.getMember().getId(),
                monday.plusDays(2),
                true,
                false,
                new BigDecimal("12.5000"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00")));
  }

  @Test
  void rankingProjectionKeepsOnlyPublicEnabledProfilesAndAllTheirParticipationPeriods() {
    var owner = portfolio("visible").getMember();
    var visible =
        InvestorProfile.create(
            owner, "visible", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    Instant first = Instant.parse("2026-10-05T00:00:00Z");
    Instant ended = Instant.parse("2026-10-12T00:00:00Z");
    Instant current = Instant.parse("2026-10-19T00:00:00Z");
    LocalDate tracking = LocalDate.of(2026, 10, 5);
    visible.startTracking(tracking);
    visible.changePublication(true, true, false, false, first);
    visible.changePublication(false, false, false, false, ended);
    visible.changePublication(true, true, false, false, current);
    entityManager.persist(visible);
    var hidden =
        InvestorProfile.create(
            portfolio("hidden").getMember(),
            "hidden",
            "",
            ExperienceLevel.BEGINNER,
            RiskProfile.BALANCED);
    entityManager.persist(hidden);
    var optedOut =
        InvestorProfile.create(
            portfolio("opted-out").getMember(),
            "opted-out",
            "",
            ExperienceLevel.BEGINNER,
            RiskProfile.BALANCED);
    optedOut.changePublication(true, false, false, false, current);
    entityManager.persist(optedOut);
    entityManager.flush();
    entityManager.clear();

    var rows = profiles.findRankingInvestors();
    assertThat(rows)
        .extracting(
            RankingInvestor::getId,
            RankingInvestor::memberId,
            RankingInvestor::getPublicId,
            RankingInvestor::getDisplayName,
            RankingInvestor::getExperienceLevel,
            RankingInvestor::getRiskProfile,
            RankingInvestor::getTrackingStartedOn)
        .containsExactly(
            tuple(
                visible.getId(),
                owner.getId(),
                visible.getPublicId(),
                "visible",
                ExperienceLevel.BEGINNER,
                RiskProfile.BALANCED,
                tracking),
            tuple(
                visible.getId(),
                owner.getId(),
                visible.getPublicId(),
                "visible",
                ExperienceLevel.BEGINNER,
                RiskProfile.BALANCED,
                tracking));
    assertThat(rows)
        .flatExtracting(RankingInvestor::getParticipations)
        .extracting(RankingParticipation::getStartedAt, RankingParticipation::getEndedAt)
        .containsExactly(tuple(first, ended), tuple(current, null));
    assertThat(profiles.findPublicRankingProfileIds()).containsExactly(visible.getId());
    assertThat(profiles.findNonPublicRankingProfileIds())
        .containsExactlyInAnyOrder(hidden.getId(), optedOut.getId());
    assertThat(
            profiles.findPublicRankingProfilesByIdIn(
                List.of(visible.getId(), hidden.getId(), optedOut.getId())))
        .extracting(
            RankingInvestor::getId,
            RankingInvestor::getDisplayName,
            RankingInvestor::getProfileImageUrl)
        .containsExactly(tuple(visible.getId(), "visible", owner.getProfileImageUrl()));
  }

  @Test
  void decisionCountsDoNotDuplicateReviewsAndRespectMemberAndTradeDate() {
    var first = portfolio("first");
    var second = portfolio("second");
    var other = portfolio("other");
    var empty = portfolio("empty");
    var asset = entityManager.persist(new Asset("test", AssetCategory.STOCK, "TEST"));
    LocalDate cutoff = LocalDate.of(2026, 10, 25);
    var reviewed = decision(first, asset, cutoff, "reviewed");
    entityManager.persist(new InvestmentReview(reviewed, "result", "difference", "next"));
    entityManager.persist(new InvestmentReview(reviewed, "second", "difference", "next"));
    decision(first, asset, cutoff.minusDays(1), "earlier-unreviewed");
    decision(second, asset, cutoff, "unreviewed");
    var future = decision(second, asset, cutoff.plusDays(1), "future");
    entityManager.persist(new InvestmentReview(future, "result", "difference", "next"));
    var excluded = decision(other, asset, cutoff, "other");
    entityManager.persist(new InvestmentReview(excluded, "result", "difference", "next"));
    entityManager.flush();
    entityManager.clear();

    assertThat(
            decisions.findCountsByMemberIdInAndTradedOnLessThanEqual(
                List.of(
                    first.getMember().getId(),
                    second.getMember().getId(),
                    empty.getMember().getId()),
                cutoff))
        .extracting(
            InvestmentDecisionCounts::getMemberId,
            InvestmentDecisionCounts::getDecisionCount,
            InvestmentDecisionCounts::getReviewedCount)
        .containsExactlyInAnyOrder(
            tuple(first.getMember().getId(), 2L, 1L), tuple(second.getMember().getId(), 1L, 0L));
  }

  private SharedPortfolio portfolio(String name) {
    var member =
        entityManager.persist(
            Member.createGoogleMember(name, name + "@example.invalid", name, null));
    return entityManager.persist(SharedPortfolio.create(member));
  }

  private PortfolioDailyReturn dailyReturn(
      SharedPortfolio portfolio, LocalDate date, int version, boolean eligible) {
    var value = new PortfolioDailyReturn(portfolio, date, version);
    if (eligible) value.completeBaseline(BigDecimal.TEN, BigDecimal.TEN, Instant.now());
    else value.exclude("MISSING_PRICE", Instant.now());
    return entityManager.persist(value);
  }

  private InvestmentDecision decision(
      SharedPortfolio portfolio, Asset asset, LocalDate date, String key) {
    return entityManager.persist(
        new InvestmentDecision(
            portfolio,
            asset,
            key,
            TransactionType.HOLD,
            1,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            date,
            "reason",
            HoldingPeriod.UNDER_ONE_MONTH,
            "expected",
            "condition",
            BigDecimal.TEN,
            false));
  }
}

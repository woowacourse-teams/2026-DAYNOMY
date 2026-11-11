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
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeagueRankingRepositoryTest extends SharedDatabaseTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private PortfolioDailyReturnRepository returns;
  @Autowired private SharedHoldingRepository holdings;
  @Autowired private org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository originals;
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
    dailyReturn(first, monday, 2, false).exclude("SNAPSHOT_PENDING", Instant.now());
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
            RankingDailyReturn::getCurrentHoldingWeight,
            RankingDailyReturn::getIneligibleReason)
        .containsExactly(
            tuple(
                first.getMember().getId(),
                monday,
                false,
                false,
                null,
                null,
                null,
                "SNAPSHOT_PENDING"),
            tuple(
                second.getMember().getId(),
                monday.plusDays(1),
                true,
                true,
                new BigDecimal("0.0000"),
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                null),
            tuple(
                first.getMember().getId(),
                monday.plusDays(2),
                true,
                false,
                new BigDecimal("12.5000"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                null));
  }

  @Test
  void rankingProjectionKeepsOnlyPublicEnabledProfilesAndAllTheirParticipationPeriods() {
    var owner = portfolio("visible").getMember();
    var visible =
        InvestorProfile.create(
            owner, "visible", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    Instant first = Instant.parse("2026-10-05T00:00:00Z");
    Instant ended = Instant.parse("2026-10-12T00:00:00Z");
    Instant current = Instant.parse("2026-10-21T00:00:00Z");
    Instant performanceStarted = Instant.parse("2026-10-19T06:30:00Z");
    LocalDate tracking = LocalDate.of(2026, 10, 5);
    visible.startTracking(tracking);
    visible.changePublication(true, true, false, false, first);
    visible.changePublication(false, false, false, false, ended);
    visible.changePublication(true, true, false, false, current);
    visible.getParticipations().getLast().includeCurrentWeekFrom(performanceStarted);
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
        .extracting(
            RankingParticipation::getStartedAt,
            RankingParticipation::getEndedAt,
            RankingParticipation::getPerformanceStartedAt)
        .containsExactly(tuple(first, ended, null), tuple(current, null, performanceStarted));
    var currentParticipation = rows.getLast().getParticipations().getFirst();
    assertThat(currentParticipation.contains(performanceStarted)).isTrue();
    assertThat(currentParticipation.eligibleFrom()).isEqualTo(LocalDate.of(2026, 10, 19));
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

  @Test
  void pendingPriceDatesUseOnlyTheLatestUnresolvedVersionAndRequestedMembers() {
    var first = portfolio("pending-first");
    var second = portfolio("pending-second");
    var other = portfolio("pending-other");
    LocalDate monday = LocalDate.of(2026, 10, 19);
    dailyReturn(first, monday.minusDays(5), 1, false);
    dailyReturn(first, monday.minusDays(5), 2, true);
    dailyReturn(first, monday.minusDays(6), 1, false).exclude("NO_HOLDINGS", Instant.now());
    dailyReturn(first, monday, 1, false).exclude("EXCHANGE_RATE_PENDING", Instant.now());
    dailyReturn(first, monday.plusDays(1), 1, false)
        .exclude("PRICE_COLLECTION_PENDING", Instant.now());
    dailyReturn(second, monday.minusDays(3), 1, false);
    dailyReturn(other, monday.minusDays(4), 1, false);
    entityManager.flush();

    assertThat(returns.findEarliestPendingPriceDateByMemberIdIn(List.of(first.getMember().getId())))
        .contains(monday);
    assertThat(
            returns.findEarliestPendingPriceDateByMemberIdIn(
                List.of(first.getMember().getId(), second.getMember().getId())))
        .contains(monday.minusDays(3));

    dailyReturn(first, monday, 2, true);
    entityManager.flush();
    assertThat(returns.findEarliestPendingPriceDateByMemberIdIn(List.of(first.getMember().getId())))
        .contains(monday.plusDays(1));

    dailyReturn(first, monday.plusDays(1), 2, false).exclude("SNAPSHOT_PENDING", Instant.now());
    entityManager.flush();
    assertThat(returns.findEarliestPendingPriceDateByMemberIdIn(List.of(first.getMember().getId())))
        .isEmpty();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.NullSource
  @org.junit.jupiter.params.provider.EnumSource(
      value = org.grit.daynomy.asset.domain.StockMarket.class,
      names = {"KOSPI", "KOSDAQ"})
  void domesticMarketsIncludeLegacyNullAndRespectActiveParticipation(
      org.grit.daynomy.asset.domain.StockMarket market) {
    var mixed = portfolio("mixed");
    var onlyUs = portfolio("only-us");
    var profile =
        InvestorProfile.create(
            mixed.getMember(), "mixed", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    profile.changePublication(true, true, false, false, Instant.now());
    entityManager.persist(profile);
    var usProfile =
        InvestorProfile.create(
            onlyUs.getMember(), "only-us", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    usProfile.changePublication(true, true, false, false, Instant.now());
    entityManager.persist(usProfile);
    var kr =
        entityManager.persist(
            Asset.listedStock(
                "domestic", "KR-legacy", market, "KR-legacy", LocalDate.of(2026, 10, 19)));
    var us =
        entityManager.persist(
            Asset.listedStock(
                "us",
                "US-test",
                org.grit.daynomy.asset.domain.StockMarket.NASDAQ,
                "US-test",
                LocalDate.of(2026, 10, 19)));
    entityManager.persist(
        new org.grit.daynomy.league.domain.SharedHolding(mixed, kr, 1, BigDecimal.TEN));
    entityManager.persist(
        new org.grit.daynomy.league.domain.SharedHolding(mixed, us, 1, BigDecimal.TEN));
    entityManager.persist(
        new org.grit.daynomy.league.domain.SharedHolding(onlyUs, us, 1, BigDecimal.TEN));
    entityManager.flush();
    assertThat(holdings.existsActiveDomesticLeagueHolding()).isTrue();
    assertThat(holdings.findActiveDomesticLeagueMemberIds())
        .containsExactly(mixed.getMember().getId());
    assertThat(holdings.hasOnlyUsHoldings(mixed.getMember().getId())).isFalse();
    assertThat(holdings.hasOnlyUsHoldings(onlyUs.getMember().getId())).isTrue();
    profile.changePublication(true, false, false, false, Instant.now());
    entityManager.flush();
    assertThat(holdings.existsActiveDomesticLeagueHolding()).isFalse();
    assertThat(holdings.findActiveDomesticLeagueMemberIds()).isEmpty();
  }

  @Test
  void readReconciliationComparesActualMembershipQuantityAndPriceForOnlyTheOwner() {
    var shared = portfolio("sync-check");
    var original =
        entityManager.persist(
            org.grit.daynomy.portfolio.domain.Portfolio.create(shared.getMember()));
    var asset =
        entityManager.persist(
            Asset.listedStock("source", "KR-sync", null, "KR-sync", LocalDate.of(2026, 10, 19)));
    var source =
        entityManager.persist(
            new org.grit.daynomy.portfolio.domain.PortfolioHolding(
                original, asset, 1, BigDecimal.TEN));
    entityManager.flush();
    Long memberId = shared.getMember().getId();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isTrue();
    var copy =
        entityManager.persist(
            new org.grit.daynomy.league.domain.SharedHolding(
                shared, asset, 1, new BigDecimal("10.00")));
    copy.changeVisibility(true);
    copy.awaitReason();
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isFalse();
    source.change(2, BigDecimal.TEN);
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isTrue();
    copy.change(2, BigDecimal.TEN);
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isFalse();
    source.change(2, new BigDecimal("11"));
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isTrue();
    copy.change(2, new BigDecimal("11"));
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isFalse();
    var other = portfolio("other-sync");
    var otherOriginal =
        entityManager.persist(
            org.grit.daynomy.portfolio.domain.Portfolio.create(other.getMember()));
    entityManager.persist(
        new org.grit.daynomy.portfolio.domain.PortfolioHolding(
            otherOriginal, asset, 99, BigDecimal.TEN));
    var unlisted =
        entityManager.persist(new Asset("unlisted", AssetCategory.STOCK, "unlisted-sync"));
    entityManager.persist(
        new org.grit.daynomy.portfolio.domain.PortfolioHolding(
            original, unlisted, 1, BigDecimal.TEN));
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isFalse();
    entityManager.remove(source);
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isTrue();
    entityManager.remove(copy);
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(memberId)).isFalse();
    var legacy = portfolio("legacy-no-source");
    entityManager.persist(
        new org.grit.daynomy.league.domain.SharedHolding(legacy, asset, 1, BigDecimal.TEN));
    entityManager.flush();
    assertThat(originals.hasSharedPortfolioChanges(legacy.getMember().getId())).isFalse();
  }

  @Test
  void reminderRecordsRespectOwnerCreationBoundaryAndAnyCompletedReview() {
    var mine = portfolio("mine");
    var other = portfolio("other");
    var asset = entityManager.persist(new Asset("test", AssetCategory.STOCK, "TEST"));
    var cutoff = Instant.parse("2026-10-26T00:00:00Z");
    var old = decision(mine, asset, LocalDate.of(2026, 10, 20), "old");
    var boundary = decision(mine, asset, LocalDate.of(2026, 10, 19), "boundary");
    var fresh = decision(mine, asset, LocalDate.of(2026, 10, 1), "fresh-backdated-trade");
    var reviewed = decision(mine, asset, LocalDate.of(2026, 10, 20), "reviewed");
    var otherRecord = decision(other, asset, LocalDate.of(2026, 10, 20), "other");
    entityManager.persist(new InvestmentReview(reviewed, "result", "difference", "next"));
    entityManager.flush();
    for (var record : List.of(old, boundary, fresh, reviewed, otherRecord)) {
      Instant created =
          record == fresh
              ? cutoff.plusNanos(1000)
              : record == boundary ? cutoff : cutoff.minusSeconds(1);
      entityManager
          .getEntityManager()
          .createNativeQuery(
              "update portfolio_transactions set created_at = :created where id = :id")
          .setParameter("created", created)
          .setParameter("id", record.getId())
          .executeUpdate();
    }
    entityManager.clear();
    assertThat(
            decisions.findUnreviewedCreatedBefore(
                mine.getMember().getId(), cutoff, PageRequest.of(0, 20)))
        .extracting(InvestmentDecision::getId)
        .containsExactly(old.getId(), boundary.getId());
  }

  @Test
  void reminderRecordsLimitTheDatabaseResultToTheOldestTwentyDecisions() {
    var mine = portfolio("bounded-reminder");
    var asset = entityManager.persist(new Asset("test", AssetCategory.STOCK, "TEST"));
    var cutoff = Instant.parse("2026-10-26T00:00:00Z");
    var records = new java.util.ArrayList<InvestmentDecision>();
    for (int index = 0; index < 25; index++) {
      records.add(decision(mine, asset, LocalDate.of(2026, 10, 20), "bounded-" + index));
    }
    entityManager.flush();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "update portfolio_transactions set created_at = :created where portfolio_id = :portfolio")
        .setParameter("created", cutoff)
        .setParameter("portfolio", mine.getId())
        .executeUpdate();
    var oldest = records.getLast();
    entityManager
        .getEntityManager()
        .createNativeQuery("update portfolio_transactions set created_at = :created where id = :id")
        .setParameter("created", cutoff.minusSeconds(1))
        .setParameter("id", oldest.getId())
        .executeUpdate();
    var expected = new java.util.ArrayList<>(records.subList(0, 19));
    expected.addFirst(oldest);
    entityManager.clear();

    assertThat(
            decisions.findUnreviewedCreatedBefore(
                mine.getMember().getId(), cutoff, PageRequest.of(0, 20)))
        .extracting(InvestmentDecision::getId)
        .containsExactlyElementsOf(expected.stream().map(InvestmentDecision::getId).toList());
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
